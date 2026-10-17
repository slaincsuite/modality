package cn.slainc.modules.policy.simulate;

import cn.slainc.modules.policy.PolicyModuleApplication;
import cn.slainc.modules.policy.PolicyTestBodies;
import cn.slainc.utils.json.JsonUtils;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 模拟与批量模拟：决策、命中明细、中文解释与耗时（真实 Tomcat + H2，无 Redis 路线）
@SpringBootTest(classes = PolicyModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicySimulateModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        for (String table : new String[]{"t_policy", "t_policy_version", "t_policy_audit", "t_policy_meta",
                "t_policy_binding", "t_policy_boundary", "t_policy_binding_source"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // t_policy 系列表由 policy 库懒建，首个用例前可能尚不存在
            }
        }
        token = JsonPath.read(login(), "$.data");
        post("/policy/manage", PolicyTestBodies.createRequest("模拟-允许读取", "user", "92001", null,
                PolicyTestBodies.document("allow-read", "allow", "modality:demo:get", "modality:demo/*")));
        post("/policy/manage", PolicyTestBodies.createRequest("模拟-拒绝删除", "global", null, null,
                PolicyTestBodies.document("deny-del", "deny", "modality:demo:delete")));
    }

    @Test
    void simulateReturnsDecisionExplainAndCost() throws Exception {
        String body = post("/policy/simulate", "{\"action\":\"modality:demo:get\","
                + "\"resource\":\"modality:demo/1\",\"userId\":\"92001\",\"loginType\":\"TENANT_USER\"}");
        assertThat(JsonPath.read(body, "$.data.decision").toString()).isEqualTo("ALLOW");
        assertThat(Integer.parseInt(JsonPath.read(body, "$.data.costMillis").toString()))
                .isGreaterThanOrEqualTo(0);
        assertThat(JsonPath.read(body, "$.data.explain[0]").toString()).contains("允许");

        String denied = post("/policy/simulate", "{\"action\":\"modality:demo:delete\","
                + "\"resource\":\"modality:demo/1\",\"userId\":\"92001\"}");
        assertThat(JsonPath.read(denied, "$.data.decision").toString()).isEqualTo("DENY");
        assertThat(denied).contains("命中拒绝语句");

        String miss = post("/policy/simulate", "{\"action\":\"modality:demo:unknown\","
                + "\"resource\":\"modality:demo/1\",\"userId\":\"92001\"}");
        assertThat(JsonPath.read(miss, "$.data.decision").toString()).isEqualTo("NOT_APPLICABLE");
    }

    @Test
    void batchSimulationProducesReport() throws Exception {
        String body = post("/policy/simulate/batch", "{\"cases\":["
                + "{\"name\":\"读取应放行\",\"request\":{\"action\":\"modality:demo:get\","
                + "\"resource\":\"modality:demo/1\",\"userId\":\"92001\"},\"expected\":\"ALLOW\"},"
                + "{\"name\":\"删除应拒绝\",\"request\":{\"action\":\"modality:demo:delete\","
                + "\"resource\":\"modality:demo/1\",\"userId\":\"92001\"},\"expected\":\"DENY\"},"
                + "{\"name\":\"误配期望\",\"request\":{\"action\":\"modality:demo:get\","
                + "\"resource\":\"modality:demo/1\",\"userId\":\"92001\"},\"expected\":\"DENY\"}]}");
        assertThat(Integer.parseInt(JsonPath.read(body, "$.data.total").toString())).isEqualTo(3);
        assertThat(Integer.parseInt(JsonPath.read(body, "$.data.passed").toString())).isEqualTo(2);
        assertThat(JsonPath.read(body, "$.data.summary[0]").toString()).contains("共 3 个用例，通过 2 个");
    }

    @Test
    void invalidSimulationRequestIsRejectedWithoutEvaluation() throws Exception {
        HttpResponse<String> missingAction = send("POST", "/policy/simulate",
                "{\"resource\":\"modality:demo/1\",\"userId\":\"92001\"}");
        assertThat(missingAction.statusCode()).isEqualTo(400);
        assertThat(missingAction.body()).contains("模拟请求必须提供 action 与 resource");

        HttpResponse<String> invalidCase = send("POST", "/policy/simulate/batch",
                "{\"cases\":[{\"name\":\"缺少请求\",\"expected\":\"ALLOW\"}]}");
        assertThat(invalidCase.statusCode()).isEqualTo(400);
        assertThat(invalidCase.body()).contains("缺少请求内容");
    }

    @Test
    void simulationExposesBoundaryOfMatchedPolicy() throws Exception {
        String policyId = post("/policy/manage", PolicyTestBodies.createRequest("带边界模拟策略",
                "global", null, null, PolicyTestBodies.document("boundary-allow", "allow",
                        "modality:boundary:get")));
        policyId = JsonPath.read(policyId, "$.data.id");
        post("/policy/manage/" + policyId + "/boundary", JsonUtils.toJson(Map.of("documentJson",
                PolicyTestBodies.document("boundary", "deny", "modality:boundary:delete"))));

        String result = post("/policy/simulate", "{\"action\":\"modality:boundary:get\","
                + "\"resource\":\"modality:boundary/1\",\"userId\":\"92001\"}");
        assertThat(Integer.parseInt(JsonPath.read(result, "$.data.boundaries.length()").toString())).isEqualTo(1);
        assertThat(JsonPath.read(result, "$.data.boundaries[0].document").toString())
                .contains("modality:boundary:delete");
    }

    private String login() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/policy-test/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return response.body();
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
}
