package cn.slainc.boot;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.security.UpmsAccountAuthProvider;
import cn.slainc.modules.upms.service.permission.PermissionService;
import cn.slainc.modules.upms.service.role.RoleService;
import cn.slainc.modules.upms.service.user.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 批次 11 整机验收：策略修改即时生效、第三方应用自助接入全链路、审核流程热配置（真实 Tomcat + 真实 Redis + H2）
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class Batch11PolicyOpenAcceptanceTest {

    private static final String PASSWORD = "Password#123";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static final String USER_POLICY_DOCUMENT = """
            {"version":"2.0","statements":[{"sid":"batch11-allow","effect":"allow",
            "actions":["modality:batch11:data:read"],"resources":["modality:batch11/data"]}]}""";

    private static final String USER_POLICY_DENY = """
            {"version":"2.0","statements":[{"sid":"batch11-deny","effect":"deny",
            "actions":["modality:batch11:data:read"],"resources":["modality:batch11/data"]}]}""";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private DynamicConfigService configService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @LocalServerPort
    private int port;

    // 本验收用 t_user 真实账号走完整登录链路，@Primary 压过 Batch4 基座的内存账号提供者（不影响其它验收默认行为）
    @TestConfiguration(proxyBeanMethods = false)
    static class DbAccountConfiguration {

        @Bean
        @Primary
        UpmsAccountAuthProvider batch11DbAccountProvider(UserMapper userMapper) {
            return new UpmsAccountAuthProvider(userMapper);
        }
    }

    @BeforeEach
    void resetTables() {
        TenantContext.set(1L);
        var staleCaches = redis.keys("modality:security:perm:*");
        if (staleCaches != null && !staleCaches.isEmpty()) {
            redis.delete(staleCaches);
        }
        configService.set("open.review.auto-approve", "false", "open");
        for (String table : new String[]{"t_policy", "t_policy_version", "t_policy_audit", "t_policy_meta",
                "t_policy_test_case", "t_policy_binding", "t_open_app", "t_open_scope", "t_open_consent",
                "t_open_credential", "t_open_app_log", "t_audit_log", "t_oauth_client", "t_consent_record",
                "t_user_role", "t_role_permission", "t_permission", "t_role", "t_user"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // 表可能懒建
            }
        }
    }

    @AfterEach
    void cleanTenant() {
        configService.set("open.review.auto-approve", "false", "open");
        TenantContext.clear();
    }

    @Test
    @DisplayName("策略修改即时生效：创建放行 → 立即 200，改 deny → 立即 403，停用 → 立即默认拒")
    void policyChangeTakesEffectImmediately() throws Exception {
        var operator = prepareOperator();
        String userId = String.valueOf(operator.getId());
        String token = login(operator.getUsername());

        HttpResponse<String> deniedByDefault = send("GET", "/batch11/policy-probe", token, null);
        assertThat(deniedByDefault.statusCode()).isEqualTo(403);

        String created = post(token, "/policy/manage", """
                {"name":"批次11用户策略","subjectType":"user","subjectId":"%s","group":"验收",
                "documentJson":%s}
                """.formatted(userId, quote(USER_POLICY_DOCUMENT)), "$.data.id");
        assertThat(created).startsWith("pol_");

        HttpResponse<String> allowed = send("GET", "/batch11/policy-probe", token, null);
        assertThat(allowed.statusCode()).as(allowed.body()).isEqualTo(200);
        assertThat(allowed.body()).contains("batch11-policy-ok");

        send("PUT", "/policy/manage/" + created, token, "{\"documentJson\":" + quote(USER_POLICY_DENY) + "}");
        HttpResponse<String> denied = send("GET", "/batch11/policy-probe", token, null);
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.body()).contains("403000");

        send("PUT", "/policy/manage/" + created + "/status", token, "{\"enabled\":false}");
        HttpResponse<String> noPolicy = send("GET", "/batch11/policy-probe", token, null);
        assertThat(noPolicy.statusCode()).isEqualTo(403);

        String simulate = post(token, "/policy/simulate", """
                {"action":"modality:batch11:data:read","resource":"modality:batch11/data",
                "userId":"%s","loginType":"TENANT_USER"}
                """.formatted(userId), "$.data.decision");
        // 策略已停用：既无 allow 也无 deny，按默认拒绝语义返回未命中
        assertThat(simulate).isEqualTo("NOT_APPLICABLE");
    }

    @Test
    @DisplayName("第三方应用自助接入：创建→提交→审核→凭证→授权→一键解约，授权事件落审计")
    void openPlatformSelfServiceOnboarding() throws Exception {
        var operator = prepareOperator();
        String token = login(operator.getUsername());

        post(token, "/open/admin/scopes", """
                {"name":"batch11_user","title":"用户基础信息","group":"验收","riskLevel":"MIDDLE"}
                """, "$.data.name");

        String appId = post(token, "/open/portal/apps", """
                {"name":"批次11自助应用","appType":"WEB","category":"工具",
                "redirectUris":["https://callback.example.com/cb"],"scopes":["batch11_user"]}
                """, "$.data.appId");
        assertThat(appId).startsWith("app_");

        post(token, "/open/portal/apps/" + appId + "/submit", null, "$.data.status");
        HttpResponse<String> reviewing = send("GET", "/open/admin/apps/" + appId, token, null);
        assertThat(reviewing.body()).contains("REVIEWING");

        post(token, "/open/admin/apps/" + appId + "/approve", "{\"reason\":\"验收通过\"}", "$.data.status");

        String issued = send("POST", "/open/portal/apps/" + appId + "/credential", token, null).body();
        String clientId = JsonPath.read(issued, "$.data.clientId").toString();
        assertThat(clientId).startsWith("cli_");
        assertThat(JsonPath.read(issued, "$.data.type").toString()).isEqualTo("OAUTH2");
        Integer clientRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_oauth_client WHERE client_id = ?", Integer.class, clientId);
        assertThat(clientRows).isEqualTo(1);

        post(token, "/open/my/consents/" + appId, "{\"scopes\":[\"batch11_user\"]}", "$.data.id");
        Integer consents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_open_consent WHERE app_id = ? AND revoked_at IS NULL", Integer.class,
                appId);
        assertThat(consents).isEqualTo(1);
        Integer auditGrants = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE category = 'PERMISSION' AND operation = 'GRANT'"
                        + " AND target = ?", Integer.class, clientId);
        assertThat(auditGrants).isEqualTo(1);

        HttpResponse<String> revoked = send("DELETE", "/open/my/consents/" + appId, token, null);
        assertThat(revoked.statusCode()).isEqualTo(200);
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_open_consent WHERE app_id = ? AND revoked_at IS NULL", Integer.class,
                appId);
        assertThat(active).isZero();

        String stats = send("GET", "/open/admin/stats", token, null).body();
        assertThat(Integer.parseInt(JsonPath.read(stats, "$.data.totalApps").toString())).isEqualTo(1);
    }

    @Test
    @DisplayName("审核流程热配置：开自动通过后新应用提交即上线，无需人工审核")
    void reviewAutoApproveHotConfig() throws Exception {
        var operator = prepareOperator();
        String token = login(operator.getUsername());
        configService.set("open.review.auto-approve", "true", "open");

        String appId = post(token, "/open/portal/apps", """
                {"name":"批次11自动通过应用","appType":"WEB",
                "redirectUris":["https://auto.example.com/cb"]}
                """, "$.data.appId");
        String status = post(token, "/open/portal/apps/" + appId + "/submit", null, "$.data.status");
        assertThat(status).isEqualTo("ONLINE");

        String logs = send("GET", "/open/admin/apps/" + appId + "/logs", token, null).body();
        assertThat(logs).contains("SUBMIT").contains("热配置自动通过");
    }

    private cn.slainc.modules.upms.entity.UserRecord prepareOperator() {
        List<String> codes = List.of("policy:manage:query", "policy:manage:create", "policy:manage:update",
                "policy:manage:version", "policy:simulate:run", "open:app:query", "open:app:review",
                "open:scope:create", "open:consent:query", "open:stats:query");
        List<Long> permissionIds = codes.stream()
                .map(code -> permissionService.create(code, "批次11-" + code, "API", null, null, null).getId())
                .toList();
        var role = roleService.create("batch11-operator", "批次11验收员", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), permissionIds);
        var user = userService.create("batch11-operator", null, PASSWORD, null, null, null,
                LoginType.TENANT_USER, 1L, "tester");
        userService.assignRoles(user.getId(), List.of(role.getId()));
        return user;
    }

    private String login(String username) throws Exception {
        HttpResponse<String> response = send("POST", "/auth/login", null, """
                {"username":"%s","password":"%s","deviceType":"PC","deviceId":"batch11-device"}
                """.formatted(username, PASSWORD));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.parse(response.body()).read("$.data.tokenValue");
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Login-Type", "TENANT_USER");
        if (token != null) {
            builder.header("satoken", token);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String post(String token, String path, String body, String jsonPath) throws Exception {
        HttpResponse<String> response = send("POST", path, token, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), jsonPath).toString();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace(" ", "") + "\"";
    }
}
