package cn.slainc.modules.open.app;

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

// 应用生命周期：自助创建 → 提交 → 审核 → 上线 → 下线 → 修改 → 重新上架 → 删除，附回调白名单严格校验（真实 Tomcat + H2）
@SpringBootTest(classes = OpenModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenAppLifecycleModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        for (String table : new String[]{"t_open_app", "t_open_scope", "t_open_consent", "t_open_credential",
                "t_open_app_log", "t_oauth_client", "t_consent_record"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // t_oauth_client 由 oauth2 库懒建，首个用例前可能尚不存在
            }
        }
        token = JsonPath.read(login(), "$.data");
    }

    @Test
    void selfServiceLifecycleWithReviewLogs() throws Exception {
        String appId = create("批次十一应用", "WEB", "https://cb.example.com/cb");
        assertThat(appId).startsWith("app_");
        assertThat(JsonPath.read(get("/open/portal/apps"), "$.data[0].status").toString()).isEqualTo("DRAFT");

        post("/open/portal/apps/" + appId + "/submit", null);
        assertThat(JsonPath.read(get("/open/admin/apps/" + appId), "$.data.app.status").toString())
                .isEqualTo("REVIEWING");

        post("/open/admin/apps/" + appId + "/approve", "{\"reason\":\"材料齐全\"}");
        assertThat(JsonPath.read(get("/open/admin/apps/" + appId), "$.data.app.status").toString())
                .isEqualTo("ONLINE");

        String logs = get("/open/admin/apps/" + appId + "/logs");
        assertThat(logs).contains("CREATE").contains("SUBMIT").contains("APPROVE").contains("材料齐全");

        HttpResponse<String> editOnline = send("PUT", "/open/portal/apps/" + appId,
                "{\"description\":\"想直接改\"}");
        assertThat(editOnline.statusCode()).isEqualTo(400);
        assertThat(editOnline.body()).contains("不允许编辑");

        post("/open/admin/apps/" + appId + "/offline", "{\"reason\":\"违规下线\"}");
        assertThat(JsonPath.read(get("/open/admin/apps/" + appId), "$.data.app.status").toString())
                .isEqualTo("OFFLINE");

        HttpResponse<String> edit = send("PUT", "/open/portal/apps/" + appId,
                "{\"description\":\"整改后的说明\"}");
        assertThat(edit.statusCode()).isEqualTo(200);

        post("/open/admin/apps/" + appId + "/republish", null);
        assertThat(JsonPath.read(get("/open/admin/apps/" + appId), "$.data.app.status").toString())
                .isEqualTo("REVIEWING");
    }

    @Test
    void redirectWhitelistIsStrict() throws Exception {
        HttpResponse<String> pathWildcard = send("POST", "/open/portal/apps",
                body("回调一", "WEB", "https://cb.example.com/cb/*"));
        assertThat(pathWildcard.statusCode()).isEqualTo(200);

        HttpResponse<String> badPath = send("POST", "/open/portal/apps",
                body("回调二", "WEB", "https://cb.example.com/*/cb"));
        assertThat(badPath.statusCode()).isEqualTo(400);
        assertThat(badPath.body()).contains("路径通配只允许出现在结尾");

        HttpResponse<String> hostWildcard = send("POST", "/open/portal/apps",
                body("回调三", "WEB", "https://*.example.com/cb"));
        assertThat(hostWildcard.statusCode()).isEqualTo(200);

        HttpResponse<String> badHost = send("POST", "/open/portal/apps",
                body("回调四", "WEB", "https://cb.*.example.com/cb"));
        assertThat(badHost.statusCode()).isEqualTo(400);
        assertThat(badHost.body()).contains("主机通配只允许");

        HttpResponse<String> badScheme = send("POST", "/open/portal/apps",
                body("回调五", "WEB", "ftp://cb.example.com/cb"));
        assertThat(badScheme.statusCode()).isEqualTo(400);
        assertThat(badScheme.body()).contains("必须是 http/https");
    }

    @Test
    void categoryAndOwnershipAreEnforced() throws Exception {
        HttpResponse<String> badCategory = send("POST", "/open/portal/apps",
                body("分类应用", "WEB", "https://cb.example.com/cb", "不存在的分类"));
        assertThat(badCategory.statusCode()).isEqualTo(400);
        assertThat(badCategory.body()).contains("分类不在允许范围内");

        String appId = create("本人应用", "WEB", "https://cb.example.com/cb");
        Integer otherDelete = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_open_app WHERE app_id = ? AND owner_id <> '93001'", Integer.class, appId);
        assertThat(otherDelete).isZero();
    }

    @Test
    void deleteOnlyAllowedForDraftOrOfflineAndCascades() throws Exception {
        String appId = create("待删应用", "WEB", "https://cb.example.com/cb");
        post("/open/portal/apps/" + appId + "/submit", null);
        post("/open/admin/apps/" + appId + "/approve", null);
        HttpResponse<String> deleteOnline = send("DELETE", "/open/admin/apps/" + appId, null);
        assertThat(deleteOnline.statusCode()).isEqualTo(400);

        post("/open/admin/apps/" + appId + "/offline", null);
        HttpResponse<String> deleted = send("DELETE", "/open/admin/apps/" + appId, null);
        assertThat(deleted.statusCode()).isEqualTo(200);
        Integer visible = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_open_app WHERE app_id = ? AND deleted = 0", Integer.class, appId);
        assertThat(visible).isZero();
        Integer softDeleted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_open_app WHERE app_id = ? AND deleted = 1", Integer.class, appId);
        assertThat(softDeleted).isEqualTo(1);
        assertThat((Integer) JsonPath.read(get("/open/portal/apps"), "$.data.length()")).isZero();
    }

    private String create(String name, String type, String redirectUri) throws Exception {
        return post("/open/portal/apps", body(name, type, redirectUri), "$.data.appId");
    }

    private String body(String name, String type, String redirectUri) {
        return body(name, type, redirectUri, null);
    }

    private String body(String name, String type, String redirectUri, String category) {
        StringBuilder json = new StringBuilder("{\"name\":\"" + name + "\",\"appType\":\"" + type + "\"");
        if (category != null) {
            json.append(",\"category\":\"").append(category).append("\"");
        }
        json.append(",\"redirectUris\":[\"").append(redirectUri).append("\"]}");
        return json.toString();
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
