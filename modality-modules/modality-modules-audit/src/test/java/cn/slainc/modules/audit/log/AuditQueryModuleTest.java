package cn.slainc.modules.audit.log;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.audit.AuditModuleApplication;
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

import static org.assertj.core.api.Assertions.assertThat;

// 审计查询端点：权限门、分页、统计与 Excel 导出（真实 HTTP）
@SpringBootTest(classes = AuditModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditQueryModuleTest {


    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private HttpClient http;

    private String token;

    private String restrictedToken;

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.set(1L);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        jdbc.execute("DELETE FROM t_audit_log");
        jdbc.update("INSERT INTO t_audit_log(audit_id, category, operation, level, module, description, success,"
                + " username, login_type, tenant_id, client_ip, create_time) VALUES"
                + " (?,?,?,?,?,?,?,?,?,?,?,?)",
                "aid-1", "LOGIN", "LOGIN", "INFO", "auth", "用户登录成功", true, "auditor", "TENANT_USER", 1L,
                "127.0.0.1", System.currentTimeMillis());
        token = loginToken(null);
        restrictedToken = loginToken(91002L);
    }

    @Test
    void pageRequiresAuthentication() throws Exception {
        HttpResponse<String> anonymous = send("GET", "/audit/logs/page", null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
    }

    @Test
    void pageReturnsFilteredRecords() throws Exception {
        HttpResponse<String> response = send("GET",
                "/audit/logs/page?category=LOGIN&operation=LOGIN&username=auditor", token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((int) com.jayway.jsonpath.JsonPath.read(response.body(), "$.data.records.length()"))
                .isGreaterThanOrEqualTo(1);
        assertThat((int) com.jayway.jsonpath.JsonPath.read(response.body(), "$.data.total")).isEqualTo(1);
    }

    @Test
    void pageCombinesDescriptionAndUsername() throws Exception {
        jdbc.update("INSERT INTO t_audit_log(audit_id, category, operation, level, module, description, success,"
                        + " username, login_type, tenant_id, client_ip, create_time) VALUES"
                        + " (?,?,?,?,?,?,?,?,?,?,?,?)",
                "aid-2", "DATA", "DELETE", "INFO", "tenant", "删除租户 acme", true, "operator-a",
                "TENANT_USER", 1L, "127.0.0.1", System.currentTimeMillis());

        HttpResponse<String> response = send("GET",
                "/audit/logs/page?username=operator-a&description=租户", token);
        HttpResponse<String> mismatch = send("GET",
                "/audit/logs/page?username=auditor&description=租户", token);

        assertThat((int) com.jayway.jsonpath.JsonPath.read(response.body(), "$.data.total")).isEqualTo(1);
        assertThat((int) com.jayway.jsonpath.JsonPath.read(mismatch.body(), "$.data.total")).isZero();
    }

    @Test
    void statsAggregatesLoginAndCategory() throws Exception {
        HttpResponse<String> response = send("GET", "/audit/logs/stats", token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Long.parseLong(com.jayway.jsonpath.JsonPath.read(response.body(), "$.data.login.total")))
                .isGreaterThanOrEqualTo(1);
        assertThat(Long.parseLong(com.jayway.jsonpath.JsonPath.read(response.body(), "$.data.categoryDistribution.LOGIN")))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void exportProducesXlsxFile() throws Exception {
        HttpResponse<byte[]> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit/logs/export"))
                        .header("satoken", token).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        byte[] body = response.body();
        assertThat(body.length).isGreaterThan(4);
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');
    }

    @Test
    void restrictedAccountCannotQueryOrExport() throws Exception {
        HttpResponse<String> page = send("GET", "/audit/logs/page", restrictedToken);
        HttpResponse<String> export = send("GET", "/audit/logs/export", restrictedToken);

        assertThat(page.statusCode()).isEqualTo(403);
        assertThat(export.statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> send(String method, String path, String tokenValue) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (tokenValue != null) {
            builder.header("satoken", tokenValue);
        }
        return http.send(builder.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String loginToken(Long userId) throws Exception {
        String path = userId == null ? "/audit-test/login" : "/audit-test/login/" + userId;
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return com.jayway.jsonpath.JsonPath.read(response.body(), "$.data");
    }
}
