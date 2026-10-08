package cn.slainc.boot;

import cn.idev.excel.EasyExcel;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.excel.UserExportRow;
import cn.slainc.modules.upms.mapper.Batch8ScopeMapper;
import cn.slainc.modules.upms.service.AccessControlService;
import cn.slainc.modules.upms.service.PermissionService;
import cn.slainc.modules.upms.service.RoleService;
import cn.slainc.modules.upms.service.SystemRegistryService;
import cn.slainc.modules.upms.service.UserService;
import cn.slainc.policy.admin.PolicyAdminService;
import cn.slainc.policy.admin.PolicyBindingService;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.model.Effect;
import cn.slainc.policy.model.PolicyDocument;
import cn.slainc.policy.model.PolicyRequest;
import cn.slainc.policy.model.PolicyStatement;
import cn.slainc.policy.model.PolicySubjectType;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// UPMS 整机验收：DB 账号真实登录 → RBAC 权限码 → 访问控制热生效 → 数据权限按部门过滤 → Excel 脱敏导出 → 策略绑定 → 日志落库
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class Batch8UpmsAcceptanceTest {

    private static final String PASSWORD = "Password#123";

    @LocalServerPort
    private int port;

    private AcceptanceHttpSupport http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private SystemRegistryService systemRegistryService;

    @Autowired
    private AccessControlService accessControlService;

    @Autowired
    private PolicyBindingService bindingService;

    @Autowired
    private PolicyAdminService policyAdminService;

    @Autowired
    private PolicyEngine policyEngine;

    @Autowired
    private cn.slainc.modules.upms.service.DeptService deptService;

    // 本验收用 t_user 真实账号走完整登录链路，@Primary 压过 Batch4 基座的内存账号提供者（不影响其它验收默认行为）
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class DbAccountConfiguration {

        @Bean
        @Primary
        cn.slainc.security.spi.AccountAuthProvider batch8DbAccountProvider(
                cn.slainc.modules.upms.mapper.UserMapper userMapper) {
            return new cn.slainc.modules.upms.security.UpmsAccountAuthProvider(userMapper);
        }
    }

    @BeforeAll
    static void prepareScopeTable(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_batch8_scope ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "tenant_id BIGINT NOT NULL DEFAULT 1,"
                + "dept_id BIGINT,"
                + "created_by BIGINT,"
                + "title VARCHAR(64))");
    }

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    @BeforeEach
    void resetUpmsTables() {
        http = new AcceptanceHttpSupport(port);
        TenantContext.set(1L);
        // H2 自增序列跨用例不复位，用户 ID 会复用，必须清掉权限缓存避免读到前一个用例的权限
        var staleCaches = redis.keys("modality:security:perm:*");
        if (staleCaches != null && !staleCaches.isEmpty()) {
            redis.delete(staleCaches);
        }
        for (String table : new String[]{"t_user_login_record", "t_user_operation_log", "t_policy_binding",
                "t_access_control", "t_system_registry", "t_role_data_scope", "t_role_permission", "t_user_role",
                "t_user_group_member", "t_user_group", "t_dept", "t_menu", "t_permission", "t_role", "t_user",
                "t_batch8_scope"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // policy 表懒建，首个用例前可能不存在
            }
        }
    }

    @Test
    @DisplayName("RBAC 闭环：DB 账号登录 → 权限码鉴权 200/403 → 角色变更即时生效")
    void rbacPermissionGateOnRealHttp() throws Exception {
        var query = permissionService.create("upms:user:query", "查用户", "API", null, null, null);
        var adminRole = roleService.create("batch8-admin", "验收管理员", null, null, null, null, null);
        roleService.assignPermissions(adminRole.getId(), List.of(query.getId()));
        var dbadmin = userService.create("batch8-admin-user", null, PASSWORD, null, null, null,
                LoginType.TENANT_USER, 1L, "tester");
        userService.assignRoles(dbadmin.getId(), List.of(adminRole.getId()));
        var plain = userService.create("batch8-plain-user", null, PASSWORD, null, null, null,
                LoginType.TENANT_USER, 1L, "tester");

        String adminToken = login("batch8-admin-user");
        assertThat(send("GET", "/upms/user/page?current=1&size=10", adminToken, null).statusCode()).isEqualTo(200);

        String plainToken = login("batch8-plain-user");
        var forbidden = send("GET", "/upms/user/page?current=1&size=10", plainToken, null);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(forbidden.body()).contains("403000");

        // 补绑角色后无需重新登录，权限缓存被失效，下一个请求立即放行
        userService.assignRoles(plain.getId(), List.of(adminRole.getId()));
        assertThat(send("GET", "/upms/user/page?current=1&size=10", plainToken, null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("访问控制：PRIVATE 系统授权即时生效，撤销后 access 权限码消失")
    void accessControlTakesEffectImmediately() throws Exception {
        var vault = systemRegistryService.create("batch8-vault", "验收机密", "secret", "PRIVATE", null, null, null,
                null);
        var user = userService.create("batch8-vault-user", null, PASSWORD, null, null, null, LoginType.TENANT_USER,
                1L, "tester");
        String token = login("batch8-vault-user");

        List<String> before = currentPermissions(token);
        assertThat(before).noneMatch(code -> code.equals("secret:batch8-vault:access"));

        accessControlService.grant(vault.getId(), List.of(user.getId()), "tester");
        assertThat(currentPermissions(token)).contains("secret:batch8-vault:access");

        accessControlService.revoke(vault.getId(), user.getId());
        assertThat(currentPermissions(token)).noneMatch(code -> code.equals("secret:batch8-vault:access"));
    }

    private List<String> currentPermissions(String token) throws Exception {
        HttpResponse<String> response = send("GET", "/upms/test/permissions", token, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.parse(response.body()).read("$.data", List.class);
    }

    @Test
    @DisplayName("数据权限：DEPT 角色经真实 HTTP 查询只见本部门数据")
    void dataScopeFiltersByDeptOverHttp() throws Exception {
        var hq = deptService.create("验收总部", null, null, null).getId();
        var rd = deptService.create("验收研发", null, null, null).getId();
        jdbc.update("INSERT INTO t_batch8_scope(tenant_id,dept_id,created_by,title) VALUES (1,?,'901','总部-数据')", hq);
        jdbc.update("INSERT INTO t_batch8_scope(tenant_id,dept_id,created_by,title) VALUES (1,?,'902','研发-数据')", rd);

        var role = roleService.create("batch8-dept-role", "部门范围", null, "DEPT", null, null, null);
        var user = userService.create("batch8-scope-user", null, PASSWORD, null, null, hq, LoginType.TENANT_USER,
                1L, "tester");
        userService.assignRoles(user.getId(), List.of(role.getId()));

        String token = login("batch8-scope-user");
        HttpResponse<String> response = send("GET", "/upms/test/scope-rows", token, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        List<String> titles = JsonPath.parse(response.body()).read("$.data", List.class);
        assertThat(titles).containsExactly("总部-数据");
    }

    @Test
    @DisplayName("Excel 导出：真实 HTTP 下载文件且手机号邮箱脱敏")
    void excelExportMasksSensitiveColumns() throws Exception {
        var query = permissionService.create("upms:user:export", "导出用户", "API", null, null, null);
        var role = roleService.create("batch8-export-role", "导出角色", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), List.of(query.getId()));
        userService.create("batch8-export-user", null, PASSWORD, "masked@example.com", "13812348888", null,
                LoginType.TENANT_USER, 1L, "tester");
        var holder = userService.create("batch8-export-holder", null, PASSWORD, null, null, null,
                LoginType.TENANT_USER, 1L, "tester");
        userService.assignRoles(holder.getId(), List.of(role.getId()));

        String token = login("batch8-export-holder");
        HttpResponse<byte[]> response = http.sendBytes("GET", "/upms/user/export", token);
        assertThat(response.statusCode()).isEqualTo(200);
        List<UserExportRow> rows = EasyExcel.read(new java.io.ByteArrayInputStream(response.body()))
                .head(UserExportRow.class).sheet().doReadSync();
        assertThat(rows).extracting(UserExportRow::getUsername).contains("batch8-export-user");
        assertThat(rows).extracting(UserExportRow::getPhone).contains("138****8888");
        assertThat(rows).extracting(UserExportRow::getEmail).contains("m*****@example.com");
        assertThat(rows).extracting(UserExportRow::getPhone)
                .noneMatch(phone -> phone != null && phone.contains("1381234"));
    }

    @Test
    @DisplayName("策略绑定：绑定用户后引擎放行，解绑后默认拒绝")
    void policyBindingTogglesEngineDecision() {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid("allow-finance");
        statement.setEffect(Effect.ALLOW.name());
        statement.setActions(List.of("finance:*"));
        statement.setResources(List.of("ledger:monthly"));
        PolicyDocument document = new PolicyDocument();
        document.setStatements(List.of(statement));
        String policyId = policyAdminService.create("验收报表", PolicySubjectType.ROLE, "nobody", null, document,
                "验收种子", "tester").getId();

        var user = userService.create("batch8-policy-user", null, PASSWORD, null, null, null, LoginType.TENANT_USER,
                1L, "tester");
        var binding = bindingService.bindToUser(policyId, user.getId(), LoginType.TENANT_USER, "tester");
        policyEngine.invalidate();
        var request = PolicyRequest.of("finance:read", "ledger:monthly")
                .userId(String.valueOf(user.getId())).loginType(LoginType.TENANT_USER.value());
        assertThat(policyEngine.evaluate(request).allowed()).isTrue();

        bindingService.unbind(binding.getId());
        policyEngine.invalidate();
        assertThat(policyEngine.evaluate(request).allowed()).isFalse();
    }

    @Test
    @DisplayName("登录记录与操作日志真实落库")
    void loginAndOperationLogsPersist() throws Exception {
        userService.create("batch8-log-user", null, PASSWORD, null, null, null, LoginType.TENANT_USER, 1L, "tester");
        String token = login("batch8-log-user");
        send("POST", "/auth/login", null, """
                {"username":"batch8-log-user","password":"wrong","deviceType":"PC","deviceId":"x"}
                """);
        assertThat(send("GET", "/upms/test/audit", token, null).statusCode()).isEqualTo(200);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user_login_record WHERE username = 'batch8-log-user'", Integer.class))
                .isGreaterThanOrEqualTo(2);
        // 操作日志经异步分发器落库，稍候片刻再断言
        Thread.sleep(800);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user_operation_log WHERE description = '演示操作'", Integer.class))
                .isGreaterThanOrEqualTo(1);
    }

    private String login(String username) throws Exception {
        return http.login(username, PASSWORD, null);
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        return http.send(method, path, token, body);
    }
}
