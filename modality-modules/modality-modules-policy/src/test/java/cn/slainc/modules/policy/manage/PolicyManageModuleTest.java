package cn.slainc.modules.policy.manage;

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

// 策略管理主链路：创建/搜索/版本/回滚/对比/复制/分组/系统策略只读（真实 Tomcat + H2，无 Redis 路线）
@SpringBootTest(classes = PolicyModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyManageModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        for (String table : new String[]{"t_policy", "t_policy_version", "t_policy_audit", "t_policy_meta",
                "t_policy_test_case", "t_policy_binding"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // t_policy 系列表由 policy 库懒建，首个用例前可能尚不存在
            }
        }
        token = JsonPath.read(login(), "$.data");
    }

    @Test
    void createSearchUpdateRollbackAndDiffWork() throws Exception {
        String document = PolicyTestBodies.document("allow-read", "allow", "modality:demo:get", "modality:demo/*");
        String created = post("/policy/manage", PolicyTestBodies.createRequest("验收策略", "user", "92001",
                "基础", document), "$.data.id");
        assertThat(created).startsWith("pol_");

        String page = get("/policy/manage/page?keyword=验收策略");
        assertThat(Integer.parseInt(JsonPath.read(page, "$.data.total").toString())).isEqualTo(1);
        assertThat(JsonPath.read(page, "$.data.records[0].group").toString()).isEqualTo("基础");

        String updated = PolicyTestBodies.document("deny-all", "deny", "*", "*");
        put("/policy/manage/" + created, PolicyTestBodies.updateRequest(updated), "$.data.version");
        String versions = get("/policy/manage/" + created + "/versions");
        assertThat(Integer.parseInt(JsonPath.read(versions, "$.data.length()").toString())).isEqualTo(2);

        String diff = get("/policy/manage/" + created + "/diff?fromVersion=1&toVersion=2");
        assertThat(diff).contains("allow-read").contains("deny-all");

        String rolled = post("/policy/manage/" + created + "/rollback/1", null, "$.data.version");
        assertThat(Integer.parseInt(rolled)).isGreaterThanOrEqualTo(3);

        String audit = get("/policy/manage/" + created + "/audit");
        assertThat(audit).contains("CREATE").contains("UPDATE").contains("ROLLBACK");
    }

    @Test
    void copyDuplicateAndGroupFilter() throws Exception {
        String document = PolicyTestBodies.document("allow-read", "allow", "demo:read");
        String source = post("/policy/manage", PolicyTestBodies.createRequest("源策略", "global", null,
                "A组", document), "$.data.id");
        post("/policy/manage", PolicyTestBodies.createRequest("另一策略", "global", null, "B组", document),
                "$.data.id");

        String copyName = post("/policy/manage/" + source + "/copy",
                "{\"name\":\"源策略-副本\"}", "$.data.name");
        assertThat(copyName).isEqualTo("源策略-副本");
        assertThat(get("/policy/manage/page?group=A组")).contains("源策略-副本");
        assertThat(Integer.parseInt(JsonPath.read(get("/policy/manage/page?group=B组"),
                "$.data.total").toString())).isEqualTo(1);
    }

    @Test
    void systemPolicyIsSeededOnceAndReadOnly() throws Exception {
        String first = post("/policy/manage/system/seed", null, "$.data.length()");
        assertThat(Integer.parseInt(first)).isEqualTo(4);
        String second = post("/policy/manage/system/seed", null, "$.data.length()");
        assertThat(Integer.parseInt(second)).isEqualTo(0);

        String systemId = JsonPath.read(get("/policy/manage/page?keyword=系统只读策略"), "$.data.records[0].id");
        HttpResponse<String> update = send("PUT", "/policy/manage/" + systemId,
                PolicyTestBodies.updateRequest(PolicyTestBodies.document("s", "allow", "demo:read")));
        assertThat(update.statusCode()).isEqualTo(403);
        assertThat(update.body()).contains("系统预置策略不允许修改");
        HttpResponse<String> delete = send("DELETE", "/policy/manage/" + systemId, null);
        assertThat(delete.statusCode()).isEqualTo(403);
    }

    @Test
    void templatesEndpointExposesPresets() throws Exception {
        String templates = get("/policy/manage/templates");
        assertThat(templates).contains("readonly").contains("full-access").contains("deny-dangerous")
                .contains("internal-only");

        String created = post("/policy/manage/from-template",
                "{\"template\":\"deny-dangerous\",\"name\":\"模板策略\",\"subjectType\":\"user\","
                        + "\"subjectId\":\"92001\",\"group\":\"模板\"}", "$.data.id");
        assertThat(created).startsWith("pol_");
    }

    @Test
    void invalidDocumentIsRejectedInChinese() throws Exception {
        HttpResponse<String> response = send("POST", "/policy/manage",
                PolicyTestBodies.createRequest("坏策略", "global", null, null, "{\"version\":\"9.9\"}"));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("策略文档版本只支持 2.0");
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

    private String post(String path, String body, String jsonPath) throws Exception {
        HttpResponse<String> response = send("POST", path, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), jsonPath).toString();
    }

    private String put(String path, String body, String jsonPath) throws Exception {
        HttpResponse<String> response = send("PUT", path, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), jsonPath).toString();
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
