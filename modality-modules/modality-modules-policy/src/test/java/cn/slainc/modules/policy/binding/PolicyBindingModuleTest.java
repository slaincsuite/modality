package cn.slainc.modules.policy.binding;

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

// 绑定门户：用户/角色/Client/批量绑定、解绑与主体视角查询（委托 upms PolicyBindingService，真实 Tomcat + H2）
@SpringBootTest(classes = PolicyModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyBindingModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        for (String table : new String[]{"t_policy", "t_policy_version", "t_policy_audit", "t_policy_meta",
                "t_policy_binding"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // t_policy 系列表由 policy 库懒建，首个用例前可能尚不存在
            }
        }
        token = JsonPath.read(login(), "$.data");
    }

    private String createPolicy(String name) throws Exception {
        return post("/policy/manage", PolicyTestBodies.createRequest(name, "global", null, null,
                PolicyTestBodies.document("allow-read", "allow", "demo:read")));
    }

    @Test
    void bindUnbindAndSubjectView() throws Exception {
        String policyId = JsonPath.read(createPolicy("绑定源策略"), "$.data.id");

        Long userBindingId = Long.parseLong(JsonPath.read(post("/policy/bindings/user",
                "{\"policyId\":\"" + policyId + "\",\"userId\":92001,\"loginType\":\"TENANT_USER\"}"),
                "$.data.id").toString());
        post("/policy/bindings/role", "{\"policyId\":\"" + policyId + "\",\"roleCode\":\"batch11-role\"}");
        post("/policy/bindings/client", "{\"policyId\":\"" + policyId + "\",\"clientId\":3001}");

        String batch = post("/policy/bindings/batch", "{\"policyId\":\"" + policyId + "\","
                + "\"subjectType\":\"user\",\"subjectIds\":[\"92002\",\"92003\"],"
                + "\"loginType\":\"TENANT_USER\"}");
        assertThat(Integer.parseInt(JsonPath.read(batch, "$.data.succeeded.length()").toString())).isEqualTo(2);

        String subjectView = get("/policy/bindings/subject?subjectType=user&subjectId=92001");
        assertThat(Integer.parseInt(JsonPath.read(subjectView, "$.data.boundPolicies.length()").toString()))
                .isEqualTo(1);

        HttpResponse<String> unbound = send("DELETE", "/policy/bindings/" + userBindingId, null);
        assertThat(unbound.statusCode()).isEqualTo(200);
        String after = get("/policy/bindings/query?subjectType=user&subjectId=92001");
        assertThat(Integer.parseInt(JsonPath.read(after, "$.data.length()").toString())).isEqualTo(0);
        Integer derived = jdbc.queryForObject("SELECT COUNT(*) FROM t_policy WHERE name LIKE ?",
                Integer.class, "%92001");
        assertThat(derived).isZero();
    }

    @Test
    void batchBindReportsFailuresWithoutStopping() throws Exception {
        String policyId = JsonPath.read(createPolicy("批量源策略"), "$.data.id");

        String result = post("/policy/bindings/batch", "{\"policyId\":\"" + policyId + "\","
                + "\"subjectType\":\"user\",\"subjectIds\":[\"92004\",\"not-a-number\"],"
                + "\"loginType\":\"TENANT_USER\"}");
        assertThat(Integer.parseInt(JsonPath.read(result, "$.data.succeeded.length()").toString())).isEqualTo(1);
        assertThat(JsonPath.read(result, "$.data.failed[0]").toString()).contains("not-a-number");
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
}
