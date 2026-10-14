package cn.slainc.boot;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import cn.slainc.sign.SignAlgorithm;
import cn.slainc.sign.composer.SignResult;
import cn.slainc.sign.key.SignKeyDefinition;
import cn.slainc.sign.key.SignKeyRegistry;
import cn.slainc.sign.tool.SignTool;
import cn.slainc.sso.client.SsoClientAdminService;
import cn.slainc.sso.client.SsoClientRecord;
import cn.slainc.sso.template.DynamicSsoClientTemplate;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "modality.security.filter.exclude-patterns=/actuator/**,/v3/api-docs/**,/swagger-ui/**,/swagger-ui.html,"
                + "/webjars/**,/favicon.ico,/error,/captcha/**,/auth/login,/auth/logout,/oauth2/jwks,"
                + "/oauth2/**,/sso/**,/batch5/**",
        "modality.sso.server.secret-key=boot-sso-server-secret",
        "modality.sso.client.enabled=true",
        "modality.sso.client.client=boot-client",
        "modality.sso.client.secret-key=boot-sso-secret",
        "modality.sso.client.login-type=APP",
        "modality.sign.keys[0].app-id=boot-app",
        "modality.sign.keys[0].algorithm=HMAC_SHA256",
        "modality.sign.keys[0].secret-base64=Ym9vdC1zaWduLXNlY3JldA=="
})
@ActiveProfiles("dev")
class Batch5ProtocolAcceptanceTest {

    private static final String PASSWORD = "Password#123";

    @Autowired
    private ClientAdminService oauth2ClientAdminService;

    @Autowired
    private SsoClientAdminService ssoClientAdminService;

    @Autowired
    private DynamicSsoClientTemplate ssoClientTemplate;

    @Autowired
    private cn.slainc.apikey.support.ApiKeyAdminService apiKeyAdminService;

    @Autowired
    private SignKeyRegistry signKeyRegistry;

    @Autowired
    private SignTool signTool;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @LocalServerPort
    private int port;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
        deleteByPrefix("satoken:");
        ssoClientTemplate.updateServerUrl(baseUrl());
        ssoClientAdminService.remove("boot-client");
        SsoClientRecord ssoClient = new SsoClientRecord();
        ssoClient.setClient("boot-client");
        ssoClient.setSecretKey("boot-sso-secret");
        ssoClient.setAllowUrl(baseUrl() + "/sso/*");
        ssoClient.setBaseUrl(baseUrl());
        ssoClient.setPushUrl("/sso/pushC");
        ssoClientAdminService.register(ssoClient);
    }

    private void deleteByPrefix(String prefix) {
        var keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void oauth2HotClientIssuesPasswordGrantToken() throws Exception {
        Oauth2ClientRecord client = new Oauth2ClientRecord();
        client.setClientId("boot-hot-client");
        client.setClientSecret("boot-hot-secret");
        client.setClientName("整机热注册应用");
        client.setContractScopes(List.of("openid"));
        client.setAllowRedirectUris(List.of("http://cb.boot.example.com/cb"));
        client.setAllowGrantTypes(List.of("password"));
        oauth2ClientAdminService.remove("boot-hot-client");
        oauth2ClientAdminService.register(client);

        HttpResponse<String> response = postForm("/oauth2/token",
                "grant_type=password&username=user&password=" + encode(PASSWORD)
                        + "&scope=openid&client_id=boot-hot-client&client_secret=boot-hot-secret");
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code"))
                .as(response.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.access_token")).isNotBlank();
    }

    @Test
    void apiKeyScopeIsolationOnRealHttp() throws Exception {
        ApiKeyModel granted = apiKeyAdminService.issue("2001", "整机验收 Key",
                List.of("order:read"), 1L, null);
        HttpResponse<String> allowed = get("/batch5/api/order?orderId=9", null,
                "X-API-Key", granted.getApiKey());
        assertThat(allowed.statusCode()).as(allowed.body()).isEqualTo(200);
        assertThat(allowed.body()).contains("订单 9 读取成功");

        ApiKeyModel ungranted = apiKeyAdminService.issue("2001", "无权限 Key",
                List.of("order:write"), 1L, null);
        HttpResponse<String> rejected = get("/batch5/api/order?orderId=9", null,
                "X-API-Key", ungranted.getApiKey());
        assertThat((int) JsonPath.<Integer>read(rejected.body(), "$.code"))
                .as(rejected.body()).isEqualTo(401000);
    }

    @Test
    void signedRequestPassesButReplayIsBlocked() throws Exception {
        SignKeyDefinition key = signKeyRegistry.find("boot-app").orElseThrow();
        Map<String, List<String>> params = Map.of("orderId", List.of("7"));
        SignResult signed = signTool.sign(SignAlgorithm.HMAC_SHA256, key, 2, params);

        HttpResponse<String> first = get("/batch5/sign/order?orderId=7", null, signHeaders(signed));
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        assertThat(first.body()).contains("签名订单 7 处理成功");

        HttpResponse<String> replay = get("/batch5/sign/order?orderId=7", null, signHeaders(signed));
        assertThat((int) JsonPath.<Integer>read(replay.body(), "$.code"))
                .as(replay.body()).isEqualTo(400100);
        assertThat(replay.body()).contains("重放");
    }

    @Test
    void ssoTicketLoginAndFullSloPush() throws Exception {
        String centerToken = loginCenter();

        HttpResponse<String> entry = get("/sso/login?redirect=" + encode(baseUrl() + "/sso/isLogin"), null);
        assertThat(entry.statusCode()).as(entry.body()).isEqualTo(302);
        HttpResponse<String> auth = get(entry.headers().firstValue("Location").orElse(""), centerToken);
        assertThat(auth.statusCode()).as(auth.body()).isEqualTo(302);
        String callbackUrl = auth.headers().firstValue("Location").orElse("");
        assertThat(callbackUrl).contains("ticket=");
        HttpResponse<String> callback = get(callbackUrl, null);
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(302);
        String clientToken = tokenFromCookie(callback);

        HttpResponse<String> clientLogin = get("/sso/isLogin", clientToken);
        assertThat(clientLogin.body()).as(clientLogin.body()).contains("true");

        HttpResponse<String> signout = get("/sso/signout?back=" + encode(baseUrl() + "/sso/isLogin"), centerToken);
        assertThat(signout.statusCode()).isIn(200, 302);

        boolean loggedOut = false;
        for (int i = 0; i < 30; i++) {
            HttpResponse<String> isLogin = get("/sso/isLogin", clientToken);
            if (isLogin.body() != null && isLogin.body().contains("false")) {
                loggedOut = true;
                break;
            }
            Thread.sleep(100);
        }
        assertThat(loggedOut).as("全端注销推送后客户端会话应下线").isTrue();
    }

    private Map<String, String> signHeaders(SignResult signed) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-App-Id", "boot-app");
        headers.put("X-Timestamp", signed.timestamp());
        headers.put("X-Nonce", signed.nonce());
        headers.put("X-Sign", signed.signature());
        headers.put("X-Sign-Algorithm", SignAlgorithm.HMAC_SHA256.name());
        return headers;
    }

    private String loginCenter() throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/auth/login"))
                .header("X-Login-Type", "TENANT_USER")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"user\",\"password\":\"" + PASSWORD + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.data.tokenValue");
    }

    private String tokenFromCookie(HttpResponse<String> response) {
        for (String cookie : response.headers().allValues("Set-Cookie")) {
            for (String part : cookie.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith("satoken=")) {
                    return trimmed.substring("satoken=".length());
                }
            }
        }
        return response.headers().firstValue("satoken").orElse(null);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return get(path, token, null, null);
    }

    private HttpResponse<String> get(String path, String token, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(path.startsWith("http") ? path : baseUrl() + path)).GET();
        if (token != null) {
            builder.header("satoken", token);
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token, String headerName, String headerValue)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(path.startsWith("http") ? path : baseUrl() + path)).GET();
        if (token != null) {
            builder.header("satoken", token);
        }
        if (headerName != null) {
            builder.header(headerName, headerValue);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
