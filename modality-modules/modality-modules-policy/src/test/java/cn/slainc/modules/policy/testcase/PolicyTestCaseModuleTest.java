package cn.slainc.modules.policy.testcase;

import cn.slainc.modules.policy.PolicyModuleApplication;
import cn.slainc.modules.policy.PolicyTestBodies;
import com.jayway.jsonpath.JsonPath;
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

// 测试用例保存与回归：启用用例参与批量运行，禁用用例跳过，单用例可独立运行（真实 Tomcat + H2，无 Redis 路线）
@SpringBootTest(classes = PolicyModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyTestCaseModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        for (String table : new String[]{"t_policy", "t_policy_version", "t_policy_audit", "t_policy_meta",
                "t_policy_test_case", "t_policy_binding", "t_policy_template", "t_policy_boundary",
                "t_policy_binding_source"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // t_policy 系列表由 policy 库懒建，首个用例前可能尚不存在
            }
        }
        token = JsonPath.read(login(), "$.data");
        post("/policy/manage", PolicyTestBodies.createRequest("用例目标策略", "global", null, null,
                PolicyTestBodies.document("allow-all", "allow", "demo:read")));
    }

    @Test
    void saveRunDisableAndRerunAsRegression() throws Exception {
        post("/policy/test-cases", "{\"name\":\"读取放行\",\"requestJson\":"
                + quote("{\"action\":\"demo:read\",\"resource\":\"demo/1\"}") + ",\"expected\":\"ALLOW\","
                + "\"enabled\":true}");
        String failedId = JsonPath.read(post("/policy/test-cases", "{\"name\":\"误配期望\","
                + "\"requestJson\":" + quote("{\"action\":\"demo:read\",\"resource\":\"demo/2\"}")
                + ",\"expected\":\"DENY\",\"enabled\":true}"), "$.data.id");

        String report = post("/policy/test-cases/run", null);
        assertThat(Integer.parseInt(JsonPath.read(report, "$.data.total").toString())).isEqualTo(2);
        assertThat(Integer.parseInt(JsonPath.read(report, "$.data.passed").toString())).isEqualTo(1);

        send("PUT", "/policy/test-cases/" + failedId, "{\"name\":\"修正期望\",\"requestJson\":"
                + quote("{\"action\":\"demo:read\",\"resource\":\"demo/2\"}")
                + ",\"expected\":\"ALLOW\",\"enabled\":false}");
        String rerun = post("/policy/test-cases/run", null);
        assertThat(Integer.parseInt(JsonPath.read(rerun, "$.data.total").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(rerun, "$.data.passed").toString())).isEqualTo(1);

        String single = post("/policy/test-cases/" + failedId + "/run", null);
        assertThat(Integer.parseInt(JsonPath.read(single, "$.data.passed").toString())).isEqualTo(1);
        assertThat(JsonPath.read(single, "$.data.results[0].name").toString()).isEqualTo("修正期望");
    }

    @Test
    void pageAndDelete() throws Exception {
        String id = JsonPath.read(post("/policy/test-cases", "{\"name\":\"待删除用例\",\"requestJson\":"
                + quote("{\"action\":\"demo:read\",\"resource\":\"demo/1\"}")
                + ",\"expected\":\"ALLOW\",\"enabled\":true}"), "$.data.id");
        assertThat(Integer.parseInt(JsonPath.read(get("/policy/test-cases/page"),
                "$.data.total").toString())).isEqualTo(1);

        HttpResponse<String> deleted = send("DELETE", "/policy/test-cases/" + id, null);
        assertThat(deleted.statusCode()).isEqualTo(200);
        assertThat(Integer.parseInt(JsonPath.read(get("/policy/test-cases/page"),
                "$.data.total").toString())).isEqualTo(0);
    }

    private String login() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/policy-test/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String get(String path) throws Exception {
        return send("GET", path, null).body();
    }

    private String post(String path, String body) throws Exception {
        HttpResponse<String> response = send("POST", path, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response.body();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Login-Type", "TENANT_USER")
                .header("satoken", token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
