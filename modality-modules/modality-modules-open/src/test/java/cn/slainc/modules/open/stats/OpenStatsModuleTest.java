package cn.slainc.modules.open.stats;

import cn.slainc.modules.open.OpenModuleApplication;
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

// 统计与 Scope 目录：规模指标、趋势、应用排行、分组统计（真实 Tomcat + H2，无 Redis 路线）
@SpringBootTest(classes = OpenModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenStatsModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        for (String table : new String[]{"t_open_app", "t_open_scope", "t_open_scope_group", "t_open_consent",
                "t_open_credential", "t_open_app_log"}) {
            jdbc.execute("DELETE FROM " + table);
        }
        token = JsonPath.read(login(), "$.data");
        post("/open/admin/scopes", "{\"name\":\"stats_base\",\"title\":\"统计基础\","
                + "\"group\":\"基础\",\"riskLevel\":\"LOW\"}");
        post("/open/admin/scopes", "{\"name\":\"stats_extra\",\"title\":\"统计扩展\","
                + "\"group\":\"扩展\",\"riskLevel\":\"MIDDLE\"}");
        for (int i = 1; i <= 3; i++) {
            String appId = post("/open/portal/apps", "{\"name\":\"统计应用" + i + "\",\"appType\":\"WEB\","
                    + "\"redirectUris\":[\"https://cb.example.com/cb" + i + "\"]}", "$.data.appId");
            post("/open/portal/apps/" + appId + "/submit", null);
            post("/open/admin/apps/" + appId + "/approve", null);
            for (int user = 1; user <= i; user++) {
                jdbc.update("INSERT INTO t_open_consent (id, user_id, app_id, scopes, granted_at, source)"
                        + " VALUES (?, ?, ?, ?, ?, 'PORTAL')", "cst_s" + i + user, String.valueOf(93000 + user),
                        appId, "[\"stats_base\"]", System.currentTimeMillis());
            }
        }
    }

    @Test
    void overviewCountsTrendAndTopApps() throws Exception {
        String overview = get("/open/admin/stats");
        assertThat(Integer.parseInt(JsonPath.read(overview, "$.data.totalApps").toString())).isEqualTo(3);
        assertThat(Integer.parseInt(JsonPath.read(overview, "$.data.onlineApps").toString())).isEqualTo(3);
        assertThat(Integer.parseInt(JsonPath.read(overview, "$.data.totalConsents").toString())).isEqualTo(6);
        assertThat(Integer.parseInt(JsonPath.read(overview, "$.data.consentUsers").toString())).isEqualTo(3);
        String today = java.time.LocalDate.now().toString();
        assertThat(Integer.parseInt(JsonPath.read(overview,
                "$['data']['trend']['" + today + "']").toString())).isEqualTo(6);
        assertThat(JsonPath.read(overview, "$.data.appTop[0].appId").toString()).startsWith("app_");
        assertThat(Integer.parseInt(JsonPath.read(overview, "$.data.appTop[0].consents").toString())).isEqualTo(3);
        assertThat(JsonPath.read(overview, "$.data.appTop[0].appName").toString()).contains("统计应用");
    }

    @Test
    void scopeCatalogWithGroupsAndHighRiskAuditFlag() throws Exception {
        String groups = get("/open/admin/scopes/groups");
        assertThat(groups).contains("基础").contains("扩展");

        post("/open/admin/scopes", "{\"name\":\"stats_danger\",\"title\":\"高危范围\","
                + "\"group\":\"基础\",\"riskLevel\":\"HIGH\",\"requiresAudit\":false}");
        String scopes = get("/open/admin/scopes?group=基础");
        assertThat(scopes).contains("stats_danger");
        Integer stored = jdbc.queryForObject(
                "SELECT requires_audit FROM t_open_scope WHERE name = 'stats_danger'", Integer.class);
        assertThat(stored).isEqualTo(1);

        HttpResponse<String> badRisk = send("POST", "/open/admin/scopes", "{\"name\":\"stats_bad\","
                + "\"title\":\"坏等级\",\"riskLevel\":\"EXTREME\"}");
        assertThat(badRisk.statusCode()).isEqualTo(400);
        assertThat(badRisk.body()).contains("风险等级只允许");
    }

    @Test
    void scopeGroupsSupportCrudRenameAndReferenceSafety() throws Exception {
        post("/open/admin/scope-groups", "{\"name\":\"business\",\"description\":\"业务范围\",\"sort\":20}");
        assertThat(get("/open/admin/scope-groups")).contains("business").contains("默认");

        post("/open/admin/scopes", "{\"name\":\"stats_business\",\"title\":\"业务 Scope\","
                + "\"group\":\"business\",\"riskLevel\":\"LOW\"}");
        assertThat(get("/open/admin/scopes/groups")).contains("\"business\":\"1\"");

        HttpResponse<String> renamed = send("PUT", "/open/admin/scope-groups/business",
                "{\"name\":\"business_v2\",\"description\":\"更新后的业务范围\",\"sort\":10}");
        assertThat(renamed.statusCode()).as(renamed.body()).isEqualTo(200);
        assertThat(get("/open/admin/scopes?group=business_v2")).contains("stats_business");

        HttpResponse<String> blocked = send("DELETE", "/open/admin/scope-groups/business_v2", null);
        assertThat(blocked.statusCode()).isEqualTo(400);
        assertThat(blocked.body()).contains("仍包含 1 个 Scope");

        assertThat(send("DELETE", "/open/admin/scopes/stats_business", null).statusCode()).isEqualTo(200);
        assertThat(send("DELETE", "/open/admin/scope-groups/business_v2", null).statusCode()).isEqualTo(200);

        HttpResponse<String> defaultDelete = send("DELETE", "/open/admin/scope-groups/默认", null);
        assertThat(defaultDelete.statusCode()).isEqualTo(400);
        assertThat(defaultDelete.body()).contains("默认分组不允许删除");
    }

    private String login() throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/open-test/login")).GET().build(),
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

    private String post(String path, String body, String jsonPath) throws Exception {
        return JsonPath.read(post(path, body), jsonPath).toString();
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
