package cn.slainc.sso;

import cn.slainc.sso.client.SsoClientAdminService;
import cn.slainc.sso.client.SsoClientRecord;
import cn.slainc.sso.logout.SsoLogoutService;
import cn.slainc.sso.template.DynamicSsoClientTemplate;
import cn.slainc.sso.template.DynamicSsoServerTemplate;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SsoTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SsoHttpAcceptanceTest {

    private static final String PASSWORD = "Password#123";

    private static final String CLIENT = "web-client";

    private static final String CLIENT_SECRET = "client-secret-001";

    @Autowired
    private SsoClientAdminService clientAdminService;

    @Autowired
    private SsoLogoutService logoutService;

    @Autowired
    private DynamicSsoClientTemplate clientTemplate;

    @Autowired
    private DynamicSsoServerTemplate serverTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @LocalServerPort
    private int port;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
        deleteByPrefix("modality:security:");
        deleteByPrefix("satoken:");
        clientTemplate.updateServerUrl(baseUrl());
        clientAdminService.list().forEach(record -> clientAdminService.remove(record.getClient()));
        registerClient(CLIENT, CLIENT_SECRET, true);
    }

    private void deleteByPrefix(String prefix) {
        var keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void registerClient(String client, String secretKey, boolean push) {
        SsoClientRecord record = new SsoClientRecord();
        record.setClient(client);
        record.setClientName(client);
        record.setSecretKey(secretKey);
        record.setAllowUrl("http://127.0.0.1:" + port + "/sso/*");
        record.setBaseUrl(baseUrl());
        record.setPushUrl("/sso/pushC");
        record.setPush(push);
        record.setSlo(true);
        clientAdminService.register(record);
    }

    @Test
    void clientLoginViaHttpTicketMode() throws Exception {
        String clientToken = loginClientFully();

        HttpResponse<String> isLogin = get("/sso/isLogin", clientToken);
        assertThat(isLogin.statusCode()).as(isLogin.body()).isEqualTo(200);
        assertThat(isLogin.body()).contains("true");
    }

    @Test
    void ticketIsOneTimeOnly() throws Exception {
        String centerToken = loginCenter();
        String ticket = obtainTicket(centerToken);

        HttpResponse<String> first = get("/sso/login?ticket=" + ticket
                + "&redirect=" + encode(baseUrl() + "/sso/isLogin"), null);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(302);
        String clientToken = tokenFromCookie(first);

        HttpResponse<String> second = get("/sso/login?ticket=" + ticket
                + "&redirect=" + encode(baseUrl() + "/sso/isLogin"), null);
        assertThat(second.statusCode()).isNotEqualTo(302);

        HttpResponse<String> isLogin = get("/sso/isLogin", clientToken);
        assertThat(isLogin.body()).contains("true");
    }

    @Test
    void fullSloLogoutPushesToClient() throws Exception {
        String centerToken = loginCenter();
        String clientToken = loginClientFully(centerToken);

        HttpResponse<String> signout = get("/sso/signout?back=/sso/isLogin", centerToken);
        assertThat(signout.statusCode()).as(signout.body()).isIn(200, 302);

        assertThatClientLoggedOutEventually(clientToken);

        HttpResponse<String> centerAgain = get("/sso/auth?client=" + CLIENT
                + "&redirect=" + encode(baseUrl() + "/sso/isLogin"), centerToken);
        assertThat(centerAgain.body()).contains("尚未登录");
    }

    @Test
    void singleClientLogoutKeepsCenterSession() throws Exception {
        String centerToken = loginCenter();
        String clientToken = loginClientFully(centerToken);

        logoutService.logoutSingleClient(CLIENT, "2001");

        assertThatClientLoggedOutEventually(clientToken);

        HttpResponse<String> centerAuth = get("/sso/auth?client=" + CLIENT
                + "&redirect=" + encode(baseUrl() + "/sso/isLogin"), centerToken);
        assertThat(centerAuth.statusCode()).as(centerAuth.body()).isEqualTo(302);
    }

    @Test
    void deviceScopedLogoutOnlyMatchesThatDevice() throws Exception {
        String centerToken = loginCenter();
        String clientToken = loginClientFully(centerToken);

        logoutService.logoutDevice("2001", "nonexistent-device");

        HttpResponse<String> clientIsLogin = get("/sso/isLogin", clientToken);
        assertThat(clientIsLogin.body()).as("其它设备的会话不应受影响").contains("true");

        HttpResponse<String> centerAuth = get("/sso/auth?client=" + CLIENT
                + "&redirect=" + encode(baseUrl() + "/sso/isLogin"), centerToken);
        assertThat(centerAuth.statusCode()).isEqualTo(302);
    }

    @Test
    void redirectOutsideAllowUrlIsRejected() throws Exception {
        String centerToken = loginCenter();
        HttpResponse<String> response = get("/sso/auth?client=" + CLIENT
                + "&redirect=" + encode("http://evil.example.net/steal"), centerToken);
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isNotEqualTo(200);
    }

    @Test
    void authWithoutLoginReturnsNotLoginView() throws Exception {
        HttpResponse<String> response = get("/sso/auth?client=" + CLIENT
                + "&redirect=" + encode(baseUrl() + "/sso/isLogin"), null);
        assertThat(response.body()).contains("尚未登录");
    }

    @Test
    void newlyRegisteredClientIsVisibleWithoutRestart() {
        registerClient("hot-client", "hot-secret", false);
        assertThat(serverTemplate.getClientNotNull("hot-client").getSecretKey()).isEqualTo("hot-secret");
        serverTemplate.checkRedirectUrl("hot-client", baseUrl() + "/sso/login");
    }

    private void assertThatClientLoggedOutEventually(String clientToken) throws InterruptedException {
        boolean clientLoggedOut = false;
        for (int i = 0; i < 30; i++) {
            try {
                HttpResponse<String> isLogin = get("/sso/isLogin", clientToken);
                if (isLogin.body() != null && isLogin.body().contains("false")) {
                    clientLoggedOut = true;
                    break;
                }
            } catch (Exception ignored) {
                // 推送尚未生效时接口可能短暂不可用，继续轮询
            }
            Thread.sleep(100);
        }
        assertThat(clientLoggedOut).as("注销推送后客户端会话应下线").isTrue();
    }

    private String loginCenter() throws Exception {
        HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/test/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"user\",\"password\":\"" + PASSWORD + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.data.tokenValue");
    }

    private String obtainTicket(String centerToken) throws Exception {
        HttpResponse<String> auth = get("/sso/auth?client=" + CLIENT
                + "&redirect=" + encode(baseUrl() + "/sso/isLogin"), centerToken);
        assertThat(auth.statusCode()).as(auth.body()).isEqualTo(302);
        String location = auth.headers().firstValue("Location").orElse("");
        return paramFromUrl(location, "ticket");
    }

    private String loginClientFully() throws Exception {
        return loginClientFully(loginCenter());
    }

    private String loginClientFully(String centerToken) throws Exception {
        HttpResponse<String> entry = get("/sso/login?redirect=" + encode(baseUrl() + "/sso/isLogin"), null);
        assertThat(entry.statusCode()).as(entry.body()).isEqualTo(302);
        String authUrl = entry.headers().firstValue("Location").orElse("");
        assertThat(authUrl).contains("/sso/auth");

        HttpResponse<String> auth = get(authUrl, centerToken);
        assertThat(auth.statusCode()).as(auth.body()).isEqualTo(302);
        String callbackUrl = auth.headers().firstValue("Location").orElse("");
        assertThat(callbackUrl).contains("ticket=");

        HttpResponse<String> callback = get(callbackUrl, null);
        assertThat(callback.statusCode()).as(callback.body()).isEqualTo(302);
        return tokenFromCookie(callback);
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
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(path.startsWith("http")
                ? path : baseUrl() + path)).GET();
        if (token != null) {
            builder.header("satoken", token);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
