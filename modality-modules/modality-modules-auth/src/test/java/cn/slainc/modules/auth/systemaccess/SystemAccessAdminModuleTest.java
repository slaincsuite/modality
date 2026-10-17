package cn.slainc.modules.auth.systemaccess;

import cn.slainc.core.context.TenantContext;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.AuthModuleApplication;
import cn.slainc.modules.auth.TestAccountRegistry;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.sso.client.SsoClientAdminService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SystemAccessAdminModuleTest {

    private static final String OAUTH2_CLIENT = "system-oauth-client";

    private static final String SSO_CLIENT = "system-sso-client";

    private static final String OAUTH2_SECRET = "oauth2-initial-secret";

    private static final String SSO_SECRET = "sso-initial-secret";

    @Autowired
    private ClientAdminService oauth2AdminService;

    @Autowired
    private SsoClientAdminService ssoAdminService;

    @Autowired
    private TestAccountRegistry registry;

    @Autowired
    private StringRedisTemplate redis;

    @LocalServerPort
    private int port;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        var keys = redis.keys("satoken:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        keys = redis.keys("modality:security:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        oauth2AdminService.remove(OAUTH2_CLIENT);
        ssoAdminService.remove(SSO_CLIENT);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void oauth2ClientLifecycleNeverReturnsStoredSecret() throws Exception {
        registry.create("system-access-admin");
        String token = login("system-access-admin");

        HttpResponse<String> create = post("/auth/oauth2/clients", """
                {
                  "clientId": "%s",
                  "clientName": "订单中心",
                  "clientSecret": "%s",
                  "scopes": ["openid", "userinfo"],
                  "redirectUris": ["https://orders.example.com/callback"],
                  "grantTypes": ["authorization_code", "refresh_token"],
                  "accessTokenTimeout": 1800,
                  "refreshTokenTimeout": 86400,
                  "maxAccessTokenCount": 3,
                  "newRefresh": false,
                  "autoConfirm": true,
                  "qpsLimit": 88,
                  "remark": "模块测试"
                }
                """.formatted(OAUTH2_CLIENT, OAUTH2_SECRET), token);
        assertThat(create.statusCode()).as(create.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(create.body(), "$.data.clientSecret")).isEqualTo(OAUTH2_SECRET);

        HttpResponse<String> list = get("/auth/oauth2/clients", token);
        assertThat(list.statusCode()).as(list.body()).isEqualTo(200);
        assertThat(list.body()).contains(OAUTH2_CLIENT).doesNotContain(OAUTH2_SECRET);

        HttpResponse<String> detail = get("/auth/oauth2/clients/" + OAUTH2_CLIENT, token);
        assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(detail.body(), "$.data.secretConfigured")).isTrue();
        assertThat(detail.body()).doesNotContain(OAUTH2_SECRET);

        HttpResponse<String> update = put("/auth/oauth2/clients/" + OAUTH2_CLIENT, """
                {
                  "clientName": "订单中心（生产）",
                  "scopes": ["openid", "pay"],
                  "redirectUris": ["https://orders.example.com/oauth/callback"],
                  "grantTypes": ["authorization_code", "refresh_token", "client_credentials"],
                  "newRefresh": true,
                  "autoConfirm": false,
                  "qpsLimit": 321
                }
                """, token);
        assertThat(update.statusCode()).as(update.body()).isEqualTo(200);
        assertThat(update.body()).contains("订单中心（生产）").contains("\"qpsLimit\":321")
                .contains("\"autoConfirm\":false");

        HttpResponse<String> disabled = post("/auth/oauth2/clients/" + OAUTH2_CLIENT + "/status",
                "{\"enabled\":false}", token);
        assertThat(disabled.statusCode()).as(disabled.body()).isEqualTo(200);
        assertThat(disabled.body()).contains("\"status\":\"disabled\"");

        HttpResponse<String> rotate = post("/auth/oauth2/clients/" + OAUTH2_CLIENT + "/secret/rotate",
                "{\"graceSeconds\":60}", token);
        assertThat(rotate.statusCode()).as(rotate.body()).isEqualTo(200);
        String rotatedSecret = JsonPath.read(rotate.body(), "$.data.clientSecret");
        assertThat(rotatedSecret).isNotBlank().isNotEqualTo(OAUTH2_SECRET);
        String previousSecretExpireAt = String.valueOf((Object) JsonPath.read(
                rotate.body(), "$.data.client.previousSecretExpireAt"));
        assertThat(Long.parseLong(previousSecretExpireAt)).isGreaterThan(System.currentTimeMillis());

        HttpResponse<String> afterRotate = get("/auth/oauth2/clients/" + OAUTH2_CLIENT, token);
        assertThat(afterRotate.body()).doesNotContain(OAUTH2_SECRET).doesNotContain(rotatedSecret);

        HttpResponse<String> delete = delete("/auth/oauth2/clients/" + OAUTH2_CLIENT, token);
        assertThat(delete.statusCode()).as(delete.body()).isEqualTo(200);
        assertThat(get("/auth/oauth2/clients/" + OAUTH2_CLIENT, token).statusCode()).isEqualTo(400);
    }

    @Test
    void ssoClientLifecycleAndSecretRotationAreManaged() throws Exception {
        registry.create("sso-access-admin");
        String token = login("sso-access-admin");

        HttpResponse<String> create = post("/auth/sso/clients", """
                {
                  "client": "%s",
                  "clientName": "客户门户",
                  "secretKey": "%s",
                  "allowUrl": "https://portal.example.com/sso/callback",
                  "baseUrl": "https://portal.example.com",
                  "pushUrl": "/sso/pushC",
                  "push": true,
                  "slo": false
                }
                """.formatted(SSO_CLIENT, SSO_SECRET), token);
        assertThat(create.statusCode()).as(create.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(create.body(), "$.data.secretKey")).isEqualTo(SSO_SECRET);

        HttpResponse<String> list = get("/auth/sso/clients", token);
        assertThat(list.statusCode()).as(list.body()).isEqualTo(200);
        assertThat(list.body()).contains(SSO_CLIENT).doesNotContain(SSO_SECRET);

        HttpResponse<String> detail = get("/auth/sso/clients/" + SSO_CLIENT, token);
        assertThat(detail.statusCode()).as(detail.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(detail.body(), "$.data.secretConfigured")).isTrue();
        assertThat(detail.body()).doesNotContain(SSO_SECRET);

        HttpResponse<String> partialUpdate = put("/auth/sso/clients/" + SSO_CLIENT, """
                {
                  "clientName": "客户门户（草稿）"
                }
                """, token);
        assertThat(partialUpdate.statusCode()).as(partialUpdate.body()).isEqualTo(200);
        assertThat(partialUpdate.body()).contains("\"push\":true").contains("\"slo\":false");

        HttpResponse<String> update = put("/auth/sso/clients/" + SSO_CLIENT, """
                {
                  "clientName": "客户门户（新版）",
                  "allowUrl": "https://portal.example.com/sso/callback\\nhttps://portal.example.com/sso/backup",
                  "baseUrl": "https://portal.example.com",
                  "pushUrl": "/sso/pushC",
                  "push": false,
                  "slo": true
                }
                """, token);
        assertThat(update.statusCode()).as(update.body()).isEqualTo(200);
        assertThat(update.body()).contains("客户门户（新版）").contains("\"push\":false")
                .contains("\"slo\":true");

        HttpResponse<String> disabled = post("/auth/sso/clients/" + SSO_CLIENT + "/status",
                "{\"enabled\":false}", token);
        assertThat(disabled.statusCode()).as(disabled.body()).isEqualTo(200);
        assertThat(disabled.body()).contains("\"status\":\"disabled\"");

        HttpResponse<String> rotate = post("/auth/sso/clients/" + SSO_CLIENT + "/secret/rotate", "", token);
        assertThat(rotate.statusCode()).as(rotate.body()).isEqualTo(200);
        String rotatedSecret = JsonPath.read(rotate.body(), "$.data.secretKey");
        assertThat(rotatedSecret).isNotBlank().isNotEqualTo(SSO_SECRET);
        assertThat(get("/auth/sso/clients/" + SSO_CLIENT, token).body()).doesNotContain(rotatedSecret);

        HttpResponse<String> delete = delete("/auth/sso/clients/" + SSO_CLIENT, token);
        assertThat(delete.statusCode()).as(delete.body()).isEqualTo(200);
        assertThat(get("/auth/sso/clients/" + SSO_CLIENT, token).statusCode()).isEqualTo(400);
    }

    @Test
    void systemAccessEndpointsEnforceQueryAndUpdatePermissionsSeparately() throws Exception {
        assertThat(get("/auth/oauth2/clients", null).statusCode()).isEqualTo(401);
        assertThat(get("/auth/sso/clients", null).statusCode()).isEqualTo(401);

        registry.create("system-access-reader");
        registry.setPermissions("system-access-reader", List.of(AuthKeys.PERMISSION_OAUTH2_CLIENT_QUERY));
        String readerToken = login("system-access-reader");

        assertThat(get("/auth/oauth2/clients", readerToken).statusCode()).isEqualTo(200);
        assertThat(get("/auth/oauth2/clients/scopes", readerToken).statusCode()).isEqualTo(200);
        assertThat(post("/auth/oauth2/clients", "{\"clientId\":\"blocked-client\"}", readerToken).statusCode())
                .isEqualTo(403);
        assertThat(get("/auth/sso/clients", readerToken).statusCode()).isEqualTo(403);

        registry.create("system-access-no-permission");
        registry.setPermissions("system-access-no-permission", List.of());
        String noPermissionToken = login("system-access-no-permission");
        assertThat(get("/auth/oauth2/clients", noPermissionToken).statusCode()).isEqualTo(403);
        assertThat(get("/auth/sso/clients", noPermissionToken).statusCode()).isEqualTo(403);
    }

    private String login(String username) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\""
                        + TestAccountRegistry.PASSWORD + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.data.tokenValue");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send(request(path).GET(), token);
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        return send(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body)), token);
    }

    private HttpResponse<String> put(String path, String body, String token) throws Exception {
        return send(request(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)), token);
    }

    private HttpResponse<String> delete(String path, String token) throws Exception {
        return send(request(path).DELETE(), token);
    }

    private HttpResponse<String> send(HttpRequest.Builder builder, String token) throws Exception {
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("X-Login-Type", "TENANT_USER");
    }
}
