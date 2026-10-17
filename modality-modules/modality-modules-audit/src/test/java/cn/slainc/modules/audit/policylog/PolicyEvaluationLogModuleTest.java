package cn.slainc.modules.audit.policylog;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.audit.AuditModuleApplication;
import cn.slainc.policy.support.PolicyAdminService;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyStatement;
import cn.slainc.policy.domain.PolicySubjectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 策略评估审计：deny 决策落 t_audit_policy_log（含命中语句明细），HTTP 真实 403
@SpringBootTest(classes = AuditModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyEvaluationLogModuleTest {


    @Autowired
    private PolicyAdminService adminService;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private HttpClient http;

    private String token;

    private String restrictedToken;

    private String denyPolicyId;

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.set(1L);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        jdbc.execute("DELETE FROM t_audit_policy_log");
        token = loginToken(null);
        restrictedToken = loginToken(91002L);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        denyPolicyId = adminService.create("审计禁写策略", PolicySubjectType.GLOBAL, null, null,
                PolicyDocument.of(statement(Effect.DENY, List.of("modality:audit-test:write"), List.of("*"))),
                null, "测试").getId();
    }

    private PolicyStatement statement(Effect effect, List<String> actions, List<String> resources) {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid("s-" + System.nanoTime());
        statement.setEffect(effect);
        statement.setActions(actions);
        statement.setResources(resources);
        return statement;
    }

    @AfterEach
    void tearDown() {
        if (denyPolicyId != null) {
            adminService.delete(denyPolicyId, "测试");
        }
    }

    @Test
    void deniedRequestIsLoggedWithStatementDetail() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit-test/policy"))
                        .header("satoken", token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat((int) com.jayway.jsonpath.JsonPath.read(response.body(), "$.code")).isEqualTo(403000);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_policy_log WHERE decision = 'DENY'"
                        + " AND action = 'modality:audit-test:write' AND login_id = '91001'", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
        String statements = jdbc.queryForObject(
                "SELECT matched_statements FROM t_audit_policy_log WHERE decision = 'DENY'"
                        + " AND action = 'modality:audit-test:write' LIMIT 1", String.class);
        assertThat(statements).contains("DENY");
    }

    @Test
    void decisionStatsReportsDenyRate() throws Exception {
        http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit-test/policy"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> stats = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/policy-logs/stats"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(stats.statusCode()).isEqualTo(200);
        assertThat(Long.parseLong(com.jayway.jsonpath.JsonPath.read(stats.body(), "$.data.denied")))
                .isGreaterThanOrEqualTo(1);
        assertThat((double) com.jayway.jsonpath.JsonPath.read(stats.body(), "$.data.denyRate"))
                .isGreaterThan(0.0);
    }

    @Test
    void policyLogPageSupportsFilters() throws Exception {
        http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit-test/policy"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> page = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/policy-logs/page?decision=DENY"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat((int) com.jayway.jsonpath.JsonPath.read(page.body(), "$.data.total")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void keywordMatchesAccountActionOrResource() throws Exception {
        long now = System.currentTimeMillis();
        insertPolicyLog("alpha", "order:read", "/orders/1", now);
        insertPolicyLog("beta", "invoice:write", "/invoices/1", now);
        insertPolicyLog("gamma", "report:read", "/reports/1", now);

        assertThat(policyPageTotal("keyword=alpha")).isEqualTo(1);
        assertThat(policyPageTotal("keyword=invoice")).isEqualTo(1);
        assertThat(policyPageTotal("keyword=reports")).isEqualTo(1);
    }

    @Test
    void exportsPolicyLogsAndRejectsRestrictedExport() throws Exception {
        long now = System.currentTimeMillis();
        insertPolicyLog("delta", "order:read", "/orders/2", now);

        HttpResponse<byte[]> exported = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/policy-logs/export?keyword=delta"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        HttpResponse<String> restricted = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/policy-logs/export"))
                .header("satoken", restrictedToken).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(exported.statusCode()).isEqualTo(200);
        assertThat(exported.body()[0]).isEqualTo((byte) 'P');
        assertThat(restricted.statusCode()).isEqualTo(403);
    }

    private int policyPageTotal(String query) throws Exception {
        HttpResponse<String> page = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/policy-logs/page?" + query))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(page.statusCode()).isEqualTo(200);
        return com.jayway.jsonpath.JsonPath.read(page.body(), "$.data.total");
    }

    private void insertPolicyLog(String loginId, String action, String resource, long createTime) {
        jdbc.update("INSERT INTO t_audit_policy_log(login_id, login_type, tenant_id, action, resource, decision,"
                        + " reason, client_ip, request_path, create_time) VALUES (?,?,?,?,?,?,?,?,?,?)",
                loginId, "TENANT_USER", 1L, action, resource, "ALLOW", "测试", "127.0.0.1", resource, createTime);
    }

    private String loginToken(Long userId) throws Exception {
        String path = userId == null ? "/audit-test/login" : "/audit-test/login/" + userId;
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return com.jayway.jsonpath.JsonPath.read(response.body(), "$.data");
    }
}
