package cn.slainc.modules.auth.oauth2;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.auth.AuthModuleApplication;
import cn.slainc.modules.upms.service.UserService;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OAuth2ConfirmModuleTest {

    private static final String CLIENT_ID = "confirm-app";

    private static final String CLIENT_SECRET = "confirm-secret";

    private static final String PASSWORD = "Password#123";

    @Autowired
    private ClientAdminService clientAdminService;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        for (String prefix : new String[]{"satoken:", "modality:security:", "modality:auth:", "modality:oauth2:"}) {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        try {
            jdbc.execute("DELETE FROM t_consent_record");
        } catch (org.springframework.dao.DataAccessException ignored) {
            // t_consent_record 由 oauth2 库懒建，首次注册 Client 前可能不存在
        }
        jdbc.execute("DELETE FROM t_user");
        clientAdminService.remove(CLIENT_ID);
        Oauth2ClientRecord client = new Oauth2ClientRecord();
        client.setClientId(CLIENT_ID);
        client.setClientSecret(CLIENT_SECRET);
        client.setClientName("确认页测试应用");
        client.setContractScopes(List.of("openid", "userinfo", "pay"));
        client.setAllowRedirectUris(List.of("http://callback.example.com/cb"));
        client.setAllowGrantTypes(List.of("authorization_code", "password"));
        clientAdminService.register(client);
        http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void confirmPageFlowAndConsentLifecycle() throws Exception {
        var user = userService.create("consentuser", "consentuser", PASSWORD, null, null, null, LoginType.TENANT_USER,
                1L, "module-test");
        String token = login("consentuser");

        String authorizeUrl = "/oauth2/authorize?response_type=code&client_id=" + CLIENT_ID
                + "&redirect_uri=" + encode("http://callback.example.com/cb")
                + "&scope=" + encode("openid pay") + "&state=cs1";
        HttpResponse<String> first = get(authorizeUrl, token);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        assertThat(first.body()).contains("需要您确认授权");
        assertThat(first.body()).contains("发起支付与资金操作");

        HttpResponse<String> confirm = postForm("/oauth2/doConfirm",
                "response_type=code&client_id=" + CLIENT_ID
                        + "&redirect_uri=" + encode("http://callback.example.com/cb")
                        + "&scope=" + encode("openid pay") + "&build_redirect_uri=true", token);
        assertThat(confirm.statusCode()).as(confirm.body()).isEqualTo(200);
        String redirectUri = JsonPath.<String>read(confirm.body(), "$.redirect_uri");
        assertThat(redirectUri).contains("code=");
        String code = paramFromUrl(redirectUri, "code");

        HttpResponse<String> tokenResponse = postForm("/oauth2/token",
                "grant_type=authorization_code&code=" + code
                        + "&redirect_uri=" + encode("http://callback.example.com/cb")
                        + "&client_id=" + CLIENT_ID + "&client_secret=" + CLIENT_SECRET, null);
        assertThat((int) JsonPath.<Integer>read(tokenResponse.body(), "$.code")).as(tokenResponse.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(tokenResponse.body(), "$.access_token")).isNotBlank();

        HttpResponse<String> consents = get("/auth/oauth2/consents", token);
        assertThat(consents.statusCode()).as(consents.body()).isEqualTo(200);
        assertThat(consents.body()).contains(CLIENT_ID);

        HttpResponse<String> revoke = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/auth/oauth2/consents/" + CLIENT_ID))
                .header("satoken", token)
                .DELETE()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(revoke.statusCode()).as(revoke.body()).isEqualTo(200);

        HttpResponse<String> afterRevoke = get(authorizeUrl + "x", token);
        assertThat(afterRevoke.statusCode()).as(afterRevoke.body()).isEqualTo(200);
        assertThat(afterRevoke.body()).contains("需要您确认授权");
    }

    private String login(String username) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.<String>read(response.body(), "$.data.tokenValue");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String form, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String paramFromUrl(String url, String name) {
        int queryIndex = url.indexOf('?');
        String query = queryIndex >= 0 ? url.substring(queryIndex + 1) : url;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
