package cn.slainc.modules.policy.binding;

import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.policy.PolicyModuleApplication;
import cn.slainc.modules.policy.PolicyTestBodies;
import cn.slainc.modules.policy.excel.PolicyBindingExportRow;
import cn.slainc.policy.domain.PolicyBinding;
import cn.slainc.policy.storage.PolicyBindingStore;
import cn.slainc.utils.json.JsonUtils;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 绑定门户：用户/角色/Client/批量绑定、解绑与主体视角查询（委托 upms PolicyBindingService，真实 Tomcat + H2）
@SpringBootTest(classes = PolicyModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyBindingModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PolicyBindingStore bindingStore;

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
        bindingStore.query(null, null, null).stream().map(PolicyBinding::getId).forEach(bindingStore::deleteById);
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
        String subjectBindings = get("/policy/bindings/query?subjectType=user&subjectId=92001");
        assertThat(JsonPath.read(subjectBindings, "$.data[0].sourcePolicyId").toString()).isEqualTo(policyId);

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

    @Test
    void groupBindingIsIdempotentAndVisibleFromSourcePolicy() throws Exception {
        String policyId = JsonPath.read(createPolicy("用户组源策略"), "$.data.id");
        post("/policy/manage/" + policyId + "/boundary", JsonUtils.toJson(Map.of("documentJson",
                PolicyTestBodies.document("boundary", "deny", "modality:demo:delete"))));
        String body = JsonUtils.toJson(Map.of("policyId", policyId, "groupId", "group-92001"));

        String first = post("/policy/bindings/group", body);
        assertThat(JsonPath.read(first, "$.data.subjectType").toString()).isEqualTo("GROUP");
        assertThat(JsonPath.read(first, "$.data.subjectId").toString()).isEqualTo("group-92001");
        assertThat(JsonPath.read(first, "$.data.sourcePolicyId").toString()).isEqualTo(policyId);
        String second = post("/policy/bindings/group", body);
        assertThat(JsonPath.read(second, "$.data.id").toString())
                .isEqualTo(JsonPath.read(first, "$.data.id").toString());
        String derivedPolicyId = JsonPath.read(first, "$.data.policyId");
        assertThat(get("/policy/manage/" + derivedPolicyId + "/boundary")).contains("modality:demo:delete");

        String sourceBindings = get("/policy/manage/" + policyId + "/bindings");
        assertThat(Integer.parseInt(JsonPath.read(sourceBindings, "$.data.length()").toString())).isEqualTo(1);
        assertThat(JsonPath.read(sourceBindings, "$.data[0].sourcePolicyId").toString()).isEqualTo(policyId);
        assertThat(get("/policy/manage/page?keyword=用户组源策略"))
                .doesNotContain("用户组源策略-group-92001");
        String query = get("/policy/bindings/query?policyId=" + policyId);
        assertThat(Integer.parseInt(JsonPath.read(query, "$.data.length()").toString())).isEqualTo(1);
        Integer derived = jdbc.queryForObject("SELECT COUNT(*) FROM t_policy WHERE name LIKE ?",
                Integer.class, "用户组源策略-%");
        assertThat(derived).isEqualTo(1);

        String bindingId = JsonPath.read(first, "$.data.id");
        assertThat(send("DELETE", "/policy/bindings/" + bindingId, null).statusCode()).isEqualTo(200);
        assertThat(Integer.parseInt(JsonPath.read(get("/policy/manage/" + policyId + "/bindings"),
                "$.data.length()").toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_policy_binding_source", Integer.class)).isZero();
    }

    @Test
    void batchGrantIsIdempotentAcrossPoliciesAndSubjects() throws Exception {
        String first = JsonPath.read(createPolicy("批量授权一"), "$.data.id");
        String second = JsonPath.read(createPolicy("批量授权二"), "$.data.id");
        String body = JsonUtils.toJson(Map.of("policyIds", List.of(first, second), "subjectType", "role",
                "subjectIds", List.of("role-a", "role-b")));

        String granted = post("/policy/manage/batch-grant", body);
        assertThat(Integer.parseInt(JsonPath.read(granted, "$.data.succeeded.length()").toString())).isEqualTo(4);
        String repeated = post("/policy/manage/batch-grant", body);
        assertThat(Integer.parseInt(JsonPath.read(repeated, "$.data.succeeded.length()").toString())).isEqualTo(4);
        Integer origins = jdbc.queryForObject("SELECT COUNT(*) FROM t_policy_binding_source", Integer.class);
        assertThat(origins).isEqualTo(4);

        HttpResponse<String> invalid = send("POST", "/policy/manage/batch-grant",
                JsonUtils.toJson(Map.of("policyIds", List.of(), "subjectType", "role",
                        "subjectIds", List.of("role-a"))));
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("批量授权必须提供策略标识列表");
    }

    @Test
    void sourceBindingsCanBeExportedAsExcel() throws Exception {
        String policyId = JsonPath.read(createPolicy("导出源策略"), "$.data.id");
        post("/policy/bindings/user", JsonUtils.toJson(Map.of("policyId", policyId, "userId", 92001,
                "loginType", "TENANT_USER")));

        HttpResponse<byte[]> exported = sendBytes("GET", "/policy/manage/" + policyId + "/bindings/export", null);
        assertThat(exported.statusCode()).as(new String(exported.body())).isEqualTo(200);
        assertThat(exported.headers().firstValue("content-disposition").orElse("")).contains(".xlsx");
        List<PolicyBindingExportRow> rows = ExcelUtils.read(new ByteArrayInputStream(exported.body()),
                PolicyBindingExportRow.class);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getSourcePolicyId()).isEqualTo(policyId);
        assertThat(rows.get(0).getSubjectType()).isEqualTo("USER");
    }

    @Test
    void currentAccountPoliciesAggregateDirectRoleAndGroupBindings() throws Exception {
        String direct = JsonPath.read(createPolicy("我的直接策略"), "$.data.id");
        String role = JsonPath.read(createPolicy("我的角色策略"), "$.data.id");
        String group = JsonPath.read(createPolicy("我的用户组策略"), "$.data.id");
        post("/policy/bindings/user", JsonUtils.toJson(Map.of("policyId", direct, "userId", 92001,
                "loginType", "TENANT_USER")));
        post("/policy/bindings/role", JsonUtils.toJson(Map.of("policyId", role,
                "roleCode", "batch11-role")));
        post("/policy/bindings/group", JsonUtils.toJson(Map.of("policyId", group,
                "groupId", "group-92001")));

        String mine = get("/auth/me/policies");
        assertThat(Integer.parseInt(JsonPath.read(mine, "$.data.directPolicies.length()").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(mine, "$.data.rolePolicies.length()").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(mine, "$.data.groupPolicies.length()").toString())).isEqualTo(1);
        assertThat(Integer.parseInt(JsonPath.read(mine, "$.data.policies.length()").toString())).isEqualTo(3);

        HttpResponse<String> anonymous = HTTP.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/auth/me/policies"))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(401);
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

    private HttpResponse<byte[]> sendBytes(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Login-Type", "TENANT_USER")
                .header("satoken", token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
