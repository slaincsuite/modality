package cn.slainc.boot;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.security.UpmsAccountAuthProvider;
import cn.slainc.modules.upms.service.PermissionService;
import cn.slainc.modules.upms.service.RoleService;
import cn.slainc.modules.upms.service.UserService;
import com.jayway.jsonpath.JsonPath;
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

// 批次 10 整机验收：登录产生审计日志、在线用户可查、可踢人（真实 Tomcat + 真实 Redis + H2）
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class Batch10AuditAcceptanceTest {

    private static final String PASSWORD = "Password#123";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

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
        UpmsAccountAuthProvider batch10DbAccountProvider(UserMapper userMapper) {
            return new UpmsAccountAuthProvider(userMapper);
        }
    }

    @BeforeEach
    void resetAuditTables() {
        TenantContext.set(1L);
        var staleCaches = redis.keys("modality:security:perm:*");
        if (staleCaches != null && !staleCaches.isEmpty()) {
            redis.delete(staleCaches);
        }
        for (String table : new String[]{"t_audit_log", "t_audit_api_log", "t_audit_policy_log",
                "t_user_role", "t_role_permission", "t_permission", "t_role", "t_user"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // 表可能懒建
            }
        }
    }

    private String prepareAuditor() {
        var query = permissionService.create("audit:log:query", "查审计", "API", null, null, null);
        var online = permissionService.create("audit:online:query", "查在线", "API", null, null, null);
        var kick = permissionService.create("audit:online:kick", "踢人", "API", null, null, null);
        var maintenance = permissionService.create("audit:maintenance:cleanup", "审计清理", "API", null, null, null);
        var export = permissionService.create("audit:log:export", "导审计", "API", null, null, null);
        var role = roleService.create("batch10-auditor", "审计验收员", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), List.of(query.getId(), online.getId(), kick.getId(),
                maintenance.getId(), export.getId()));
        var user = userService.create("batch10-auditor", null, PASSWORD, null, null, null,
                LoginType.TENANT_USER, 1L, "tester");
        userService.assignRoles(user.getId(), List.of(role.getId()));
        return user.getUsername();
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

    private HttpResponse<byte[]> sendBytes(String path, String token) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("satoken", token)
                .header("X-Login-Type", "TENANT_USER")
                .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private String login(String username, String deviceId) throws Exception {
        HttpResponse<String> response = send("POST", "/auth/login", null, """
                {"username":"%s","password":"%s","deviceType":"PC","deviceId":"%s"}
                """.formatted(username, PASSWORD, deviceId));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.parse(response.body()).read("$.data.tokenValue");
    }

    @Test
    @DisplayName("登录产生日志 + 在线用户可查（真实 Redis 扫描）")
    void loginProducesAuditLogAndOnlineListShowsSession() throws Exception {
        String auditor = prepareAuditor();
        String token = login(auditor, "batch10-online");
        Thread.sleep(600);
        Integer loginLogs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE category = 'LOGIN' AND operation = 'LOGIN'"
                        + " AND username = ? AND success = 1", Integer.class, auditor);
        assertThat(loginLogs).isGreaterThanOrEqualTo(1);

        HttpResponse<String> page = send("GET",
                "/audit/online/page?type=TENANT_USER&keyword=" + "9", token, null);
        assertThat(page.statusCode()).as(page.body()).isEqualTo(200);
        assertThat((int) JsonPath.read(page.body(), "$.data.page.records.length()")).isGreaterThanOrEqualTo(1);
        assertThat(page.body()).contains(auditor);

        HttpResponse<String> stats = send("GET", "/audit/online/stats", token, null);
        assertThat(stats.statusCode()).isEqualTo(200);
        assertThat(Long.parseLong(JsonPath.read(stats.body(), "$.data.total"))).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("可踢人：单 token 踢出 → 被踢会话立即 401，双层审计落库")
    void kickOnlineUserKillsSessionAndWritesAuditTrail() throws Exception {
        String auditor = prepareAuditor();
        var victim = userService.create("batch10-victim", null, PASSWORD, null, null, null,
                LoginType.TENANT_USER, 1L, "tester");
        String adminToken = login(auditor, "batch10-kick-admin");
        String victimToken = login(victim.getUsername(), "batch10-kick-victim");
        // 被踢前 victim 已登录但无 audit 权限码：真实 403（区别于未登录的 401）
        assertThat(send("GET", "/audit/logs/page", victimToken, null).statusCode()).isEqualTo(403);

        HttpResponse<String> kick = send("POST", "/audit/online/kick", adminToken, """
                {"type":"TENANT_USER","token":"%s","reason":"验收踢出"}
                """.formatted(victimToken));
        assertThat(kick.statusCode()).as(kick.body()).isEqualTo(200);
        assertThat((boolean) JsonPath.read(kick.body(), "$.data.kicked")).isTrue();

        assertThat(send("GET", "/audit/logs/page", victimToken, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/audit/logs/page", adminToken, null).statusCode()).isEqualTo(200);
        Integer adminAction = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE module = 'audit' AND description LIKE '%验收踢出%'",
                Integer.class);
        Integer kickedEvent = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE module = 'session' AND description LIKE '%强制下线%'",
                Integer.class);
        assertThat(adminAction).isGreaterThanOrEqualTo(1);
        assertThat(kickedEvent).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("权限门：无 audit 权限码的用户访问审计端点真实 403")
    void userWithoutAuditPermissionGetsReal403() throws Exception {
        prepareAuditor();
        var plain = userService.create("batch10-plain", null, PASSWORD, null, null, null,
                LoginType.TENANT_USER, 1L, "tester");
        String plainToken = login(plain.getUsername(), "batch10-plain");
        HttpResponse<String> forbidden = send("GET", "/audit/logs/page", plainToken, null);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).contains("403000");
    }

    @Test
    @DisplayName("API 访问日志：错误请求必记，导出为合法 Excel 文件")
    void apiLogRecordsErrorsAndExportProducesXlsx() throws Exception {
        String auditor = prepareAuditor();
        String token = login(auditor, "batch10-export");
        HttpResponse<String> notFound = send("GET", "/no-such-path-batch10", token, null);
        assertThat(notFound.statusCode()).isEqualTo(404);
        Thread.sleep(800);
        Integer errors = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_api_log WHERE request_path = '/no-such-path-batch10'"
                        + " AND error = 1", Integer.class);
        assertThat(errors).isGreaterThanOrEqualTo(1);

        HttpResponse<byte[]> export = sendBytes("/audit/logs/export?limit=100", token);
        assertThat(export.statusCode()).isEqualTo(200);
        byte[] body = export.body();
        assertThat(body.length).isGreaterThan(4);
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');
    }

    @Test
    @DisplayName("手动清理：维护端点按保留天数清理并返回报告")
    void manualCleanupRunsThroughMaintenanceEndpoint() throws Exception {
        String auditor = prepareAuditor();
        String token = login(auditor, "batch10-cleanup");
        long stale = System.currentTimeMillis() - 100L * 24 * 3600 * 1000;
        jdbc.update("INSERT INTO t_audit_log(audit_id, category, operation, level, module, description, success,"
                + " create_time) VALUES ('stale-batch10','LOGIN','LOGIN','INFO','auth','旧记录',1,?)", stale);
        HttpResponse<String> response = send("POST", "/audit/maintenance/cleanup", token, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((int) JsonPath.read(response.body(), "$.data.retentionDays")).isEqualTo(90);
        Integer staleLeft = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE audit_id = 'stale-batch10'", Integer.class);
        assertThat(staleLeft).isZero();
    }
}
