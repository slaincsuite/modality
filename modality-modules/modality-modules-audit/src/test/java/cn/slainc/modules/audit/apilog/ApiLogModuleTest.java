package cn.slainc.modules.audit.apilog;

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

// API 访问日志过滤器：错误请求必记、采样路径、异步落库
@SpringBootTest(classes = AuditModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiLogModuleTest {


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
        jdbc.execute("DELETE FROM t_audit_api_log");
        token = loginToken(null);
        restrictedToken = loginToken(91002L);
    }

    @Test
    void errorRequestIsAlwaysLogged() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit-test/not-exists"))
                        .header("satoken", token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);
        Thread.sleep(600);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_api_log WHERE request_path = '/audit-test/not-exists'"
                        + " AND error = 1 AND status_code = 404", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void protectedEndpointRejectRecordsErrorWithLogin() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit/logs/page")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
        Thread.sleep(600);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_api_log WHERE request_path = '/audit/logs/page'"
                        + " AND error = 1", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void normalRequestSampledByFullRate() throws Exception {
        // 测试配置采样率默认 1.0，正常请求也应记录（阈值 60 秒不会误标慢请求）
        http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit-test/annotated"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        Thread.sleep(600);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_api_log WHERE request_path = '/audit-test/annotated'"
                        + " AND slow = 0", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void pageEndpointFiltersSlowAndErrors() throws Exception {
        jdbc.update("INSERT INTO t_audit_api_log(trace_id, login_id, login_type, tenant_id, client_ip,"
                + " http_method, request_path, status_code, cost_millis, slow, error, create_time)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                "trace-1", "91001", "TENANT_USER", 1L, "127.0.0.1", "GET", "/slow-path", 200, 9999, true, false,
                System.currentTimeMillis());
        HttpResponse<String> slow = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/api-logs/slow"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(slow.statusCode()).isEqualTo(200);
        assertThat((int) com.jayway.jsonpath.JsonPath.read(slow.body(), "$.data.total")).isEqualTo(1);

        HttpResponse<String> errors = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/api-logs/errors"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(errors.statusCode()).isEqualTo(200);
        assertThat((int) com.jayway.jsonpath.JsonPath.read(errors.body(), "$.data.total")).isEqualTo(0);
    }

    @Test
    void pageFiltersByStatusRangeAndKeepsExactStatusCompatibility() throws Exception {
        long now = System.currentTimeMillis();
        insertApiLog("/range-400", 400, false, false, now);
        insertApiLog("/range-500", 500, false, true, now);
        insertApiLog("/range-200", 200, false, false, now);

        HttpResponse<String> clientErrors = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port
                                + "/audit/api-logs/page?path=/range-&statusRange=4xx"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> exact = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port
                                + "/audit/api-logs/page?path=/range-&statusRange=5xx&statusCode=500"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> invalid = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/api-logs/page?statusRange=3xx"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat((int) com.jayway.jsonpath.JsonPath.read(clientErrors.body(), "$.data.total")).isEqualTo(1);
        assertThat((int) com.jayway.jsonpath.JsonPath.read(exact.body(), "$.data.total")).isEqualTo(1);
        assertThat(invalid.statusCode()).isEqualTo(400);
    }

    @Test
    void exportSupportsSlowAndErrorScopes() throws Exception {
        long now = System.currentTimeMillis();
        insertApiLog("/scope-slow", 200, true, false, now);
        insertApiLog("/scope-error", 500, false, true, now);

        HttpResponse<byte[]> slow = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port
                                + "/audit/api-logs/export?scope=slow&path=/scope-slow"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        HttpResponse<byte[]> error = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port
                                + "/audit/api-logs/export?scope=error&path=/scope-error"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        HttpResponse<String> invalid = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/api-logs/export?scope=unknown"))
                .header("satoken", token).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(slow.statusCode()).isEqualTo(200);
        assertThat(slow.body()[0]).isEqualTo((byte) 'P');
        assertThat(error.statusCode()).isEqualTo(200);
        assertThat(error.body()[0]).isEqualTo((byte) 'P');
        assertThat(invalid.statusCode()).isEqualTo(400);
    }

    @Test
    void restrictedAccountCannotQueryOrExport() throws Exception {
        HttpResponse<String> page = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/api-logs/page"))
                .header("satoken", restrictedToken).GET().build(), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> export = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/audit/api-logs/export"))
                .header("satoken", restrictedToken).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(page.statusCode()).isEqualTo(403);
        assertThat(export.statusCode()).isEqualTo(403);
    }

    private void insertApiLog(String path, int statusCode, boolean slow, boolean error, long createTime) {
        jdbc.update("INSERT INTO t_audit_api_log(trace_id, login_id, login_type, tenant_id, client_ip,"
                        + " http_method, request_path, status_code, cost_millis, slow, error, create_time)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                "trace-" + path, "91001", "TENANT_USER", 1L, "127.0.0.1", "GET", path, statusCode, 10L,
                slow, error, createTime);
    }

    private String loginToken(Long userId) throws Exception {
        String path = userId == null ? "/audit-test/login" : "/audit-test/login/" + userId;
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return com.jayway.jsonpath.JsonPath.read(response.body(), "$.data");
    }
}
