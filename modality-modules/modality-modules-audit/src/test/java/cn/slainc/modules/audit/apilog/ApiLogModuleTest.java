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

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.set(1L);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        jdbc.execute("DELETE FROM t_audit_api_log");
        token = loginToken();
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

    private String loginToken() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit-test/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return com.jayway.jsonpath.JsonPath.read(response.body(), "$.data");
    }
}
