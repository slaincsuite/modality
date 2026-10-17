package cn.slainc.modules.auth.security;

import cn.slainc.core.context.TenantContext;
import cn.slainc.modules.auth.AuthModuleApplication;
import cn.slainc.modules.auth.FakeFeishuProvider;
import cn.slainc.modules.auth.TestAccountRegistry;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.ThirdPartyUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthSecurityAdminModuleTest {

    @Autowired
    private TestAccountRegistry registry;

    @Autowired
    private FakeFeishuProvider fakeProvider;

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
        for (String prefix : new String[]{"satoken:", "modality:security:", "modality:auth:", "modality:thirdauth:"}) {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        for (String table : new String[]{"t_mfa_factor", "t_auth_passkey", "t_third_account_binding",
                "t_auth_security_policy", "t_auth_feishu_app_config", "t_auth_third_sync_record"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (RuntimeException ignored) {
                // 建表初始化器尚未执行时，首个用例允许表不存在
            }
        }
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void securityPoliciesArePersistedAndValidated() throws Exception {
        registry.create("policy-user");
        String token = login("policy-user");

        HttpResponse<String> defaults = get("/auth/session-policy", token);
        assertThat(defaults.statusCode()).as(defaults.body()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(defaults.body(), "$.data.idleTimeoutSeconds")).isPositive();
        assertThat((Integer) JsonPath.read(defaults.body(), "$.data.maxSessionSeconds")).isPositive();

        HttpResponse<String> mfa = put("/auth/mfa/policy",
                "{\"loginProtect\":false,\"operationProtect\":false}", token);
        assertThat(mfa.statusCode()).as(mfa.body()).isEqualTo(200);
        assertThat(get("/auth/mfa/policy", token).body()).contains("\"loginProtect\":false");

        HttpResponse<String> loginPolicy = put("/auth/login-policy", "{\"remoteCheck\":false}", token);
        assertThat(loginPolicy.statusCode()).as(loginPolicy.body()).isEqualTo(200);
        assertThat(get("/auth/login-policy", token).body()).contains("\"remoteCheck\":false");

        HttpResponse<String> session = put("/auth/session-policy",
                "{\"idleTimeoutSeconds\":900,\"maxSessionSeconds\":3600}", token);
        assertThat(session.statusCode()).as(session.body()).isEqualTo(200);
        assertThat(get("/auth/session-policy", token).body())
                .contains("\"idleTimeoutSeconds\":900")
                .contains("\"maxSessionSeconds\":3600");

        HttpResponse<String> invalid = put("/auth/session-policy",
                "{\"idleTimeoutSeconds\":7200,\"maxSessionSeconds\":3600}", token);
        assertThat(invalid.statusCode()).as(invalid.body()).isEqualTo(400);
        assertThat(invalid.body()).contains("不能小于无操作超时");
        assertThat(get("/auth/mfa/policy", null).statusCode()).isEqualTo(401);
    }

    @Test
    void missingPermissionReturnsForbidden() throws Exception {
        registry.create("policy-reader");
        registry.setPermissions("policy-reader", List.of());
        String token = login("policy-reader");

        HttpResponse<String> response = get("/auth/mfa/policy", token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
    }

    @Test
    void adminCanInspectAndUnbindUserMfa() throws Exception {
        PasswordAccount account = registry.create("mfa-admin-target");
        registry.create("mfa-admin-operator");
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO t_mfa_factor(tenant_id, user_id, login_type, username, factor_type, secret_cipher,"
                        + " activated, recovery_codes, fail_count, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                1L, account.userId(), "TENANT_USER", account.username(), "TOTP", "cipher", true, "h1,h2", 0,
                now, now);
        String token = login("mfa-admin-operator");

        HttpResponse<String> status = get("/upms/user/" + account.userId() + "/mfa", token);
        assertThat(status.statusCode()).as(status.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(status.body(), "$.data.enrolled")).isTrue();
        assertThat((Boolean) JsonPath.read(status.body(), "$.data.activated")).isTrue();
        assertThat((String) JsonPath.read(status.body(), "$.data.boundAt")).isNotBlank();

        HttpResponse<String> delete = delete("/upms/user/" + account.userId() + "/mfa", token);
        assertThat(delete.statusCode()).as(delete.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_mfa_factor WHERE user_id = ?", Long.class,
                account.userId())).isZero();

        HttpResponse<String> repeat = delete("/upms/user/" + account.userId() + "/mfa", token);
        assertThat(repeat.statusCode()).as(repeat.body()).isEqualTo(400);
        assertThat(repeat.body()).contains("未绑定二次验证");
    }

    @Test
    void passkeyMetadataCanBeManagedWithoutAssertionVerification() throws Exception {
        PasswordAccount account = registry.create("passkey-user");
        registry.create("passkey-operator");
        String token = login("passkey-operator");
        String path = "/upms/user/" + account.userId() + "/passkeys";
        String credentialId = "cred-" + account.userId();

        HttpResponse<String> create = post(path, "{\"credentialId\":\"" + credentialId
                + "\",\"name\":\"MacBook 指纹\",\"publicKey\":\"pQECAwQ=\"}", token);
        assertThat(create.statusCode()).as(create.body()).isEqualTo(200);
        assertThat(get(path, token).body()).contains(credentialId).contains("MacBook 指纹");

        HttpResponse<String> duplicate = post(path, "{\"credentialId\":\"" + credentialId
                + "\",\"name\":\"重复\",\"publicKey\":\"pQECAwQ=\"}", token);
        assertThat(duplicate.statusCode()).as(duplicate.body()).isEqualTo(400);
        assertThat(duplicate.body()).contains("已存在");

        HttpResponse<String> invalid = post(path, "{\"credentialId\":\"bad\",\"name\":\"\",\"publicKey\":\"x\"}",
                token);
        assertThat(invalid.statusCode()).as(invalid.body()).isEqualTo(400);

        HttpResponse<String> delete = delete(path + "/" + credentialId, token);
        assertThat(delete.statusCode()).as(delete.body()).isEqualTo(200);
        assertThat(get(path, token).body()).contains("\"data\":[]").doesNotContain(credentialId);
    }

    @Test
    void consoleBindingQrIsIsolatedFromLoginQrAndReturnsBindingResult() throws Exception {
        PasswordAccount account = registry.create("console-bind");
        String token = login("console-bind");
        fakeProvider.nextUser(feishuUser("open-console-bind", "union-console-bind"));

        HttpResponse<String> create = post("/auth/third/feishu/bind-qr", "{}", token);
        assertThat(create.statusCode()).as(create.body()).isEqualTo(200);
        String ticketId = JsonPath.read(create.body(), "$.data.ticketId");
        assertThat(JsonPath.<String>read(create.body(), "$.data.content")).contains(ticketId);

        assertThat(get("/auth/third/feishu/bind-qr/" + ticketId, token).body()).contains("WAITING");
        assertThat(get("/auth/third/callback/feishu?code=bind-code&state=" + ticketId, null).statusCode())
                .isEqualTo(200);

        HttpResponse<String> confirmed = get("/auth/third/feishu/bind-qr/" + ticketId, token);
        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);
        assertThat(confirmed.body()).contains("CONFIRMED").contains("open-console-bind");
        assertThat(get("/auth/third/bindings", token).body()).contains("open-console-bind");

        registry.create("other-console-user");
        String otherToken = login("other-console-user");
        assertThat(get("/auth/third/feishu/bind-qr/" + ticketId, otherToken).statusCode()).isEqualTo(403);
        assertThat(account.userId()).isNotNull();
    }

    @Test
    void adminCanStartListAndRemoveThirdBinding() throws Exception {
        PasswordAccount target = registry.create("third-binding-target");
        registry.create("third-binding-admin");
        String token = login("third-binding-admin");
        fakeProvider.nextUser(feishuUser("open-admin-binding", "union-admin-binding"));

        HttpResponse<String> create = post("/upms/user/" + target.userId() + "/third-binding",
                "{\"platform\":\"feishu\"}", token);
        assertThat(create.statusCode()).as(create.body()).isEqualTo(200);
        String ticketId = JsonPath.read(create.body(), "$.data.ticketId");
        assertThat(get("/auth/third/callback/feishu?code=admin-bind&state=" + ticketId, null).statusCode())
                .isEqualTo(200);

        HttpResponse<String> list = get("/upms/user/" + target.userId() + "/third-binding", token);
        assertThat(list.statusCode()).as(list.body()).isEqualTo(200);
        assertThat(list.body()).contains("open-admin-binding");

        HttpResponse<String> unbind = delete("/upms/user/" + target.userId()
                + "/third-binding/feishu/open-admin-binding", token);
        assertThat(unbind.statusCode()).as(unbind.body()).isEqualTo(200);
        assertThat(get("/upms/user/" + target.userId() + "/third-binding", token).body())
                .doesNotContain("open-admin-binding");
    }

    @Test
    void feishuConfigIsMaskedAndSyncRecordsExport() throws Exception {
        registry.create("feishu-config-operator");
        String token = login("feishu-config-operator");

        HttpResponse<String> initial = get("/auth/third/feishu/config", token);
        assertThat(initial.statusCode()).as(initial.body()).isEqualTo(200);
        assertThat(initial.body()).contains("cli-key").contains("******").doesNotContain("cli-secret");

        HttpResponse<String> update = put("/auth/third/feishu/config",
                "{\"appId\":\"cli-new\",\"appSecret\":\"new-secret\",\"verificationToken\":\"verify-token\","
                        + "\"encryptKey\":\"encrypt-key\",\"callback\":\"https://example.com/auth/third/callback/feishu\"}",
                token);
        assertThat(update.statusCode()).as(update.body()).isEqualTo(200);
        HttpResponse<String> config = get("/auth/third/feishu/config", token);
        assertThat(config.body()).contains("cli-new").contains("******").doesNotContain("new-secret");

        HttpResponse<String> enabled = put("/auth/third/feishu/status", "{\"enabled\":true}", token);
        assertThat(enabled.statusCode()).as(enabled.body()).isEqualTo(200);
        assertThat(enabled.body()).contains("\"enabled\":true");

        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO t_auth_third_sync_record(platform, sync_type, status, total_count, success_count,"
                        + " failure_count, message, operator, started_at, finished_at, create_time)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                "feishu", "CONTACT", "SUCCESS", 8, 8, 0, "同步完成", "tester", now, now, now);
        HttpResponse<String> page = get("/auth/third/feishu/sync-records?current=1&size=20", token);
        assertThat(page.statusCode()).as(page.body()).isEqualTo(200);
        assertThat(page.body()).contains("\"total\":1").contains("SUCCESS");

        HttpResponse<String> export = get("/auth/third/feishu/sync-records/export", token);
        assertThat(export.statusCode()).as(export.body()).isEqualTo(200);
        assertThat(export.body()).contains("时间,类型,结果").contains("SUCCESS");

        HttpResponse<String> reset = post("/auth/third/feishu/secret/reset", "{}", token);
        assertThat(reset.statusCode()).as(reset.body()).isEqualTo(200);
        assertThat(reset.body()).contains("\"enabled\":false");
    }

    private String login(String username) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + TestAccountRegistry.PASSWORD + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.data.tokenValue");
    }

    private ThirdPartyUser feishuUser(String openId, String unionId) {
        ThirdPartyUser user = new ThirdPartyUser();
        user.setPlatform(ThirdPartyPlatform.FEISHU);
        user.setOpenId(openId);
        user.setUnionId(unionId);
        user.setNickname("飞书绑定用户");
        user.setAvatar("https://cdn.example.com/" + openId + ".png");
        return user;
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = request(path).GET();
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = request(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = request(path).DELETE();
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
