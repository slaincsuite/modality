package cn.slainc.modules.open.credential;

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

// 凭证自助管理：oauth2 桥真实落 t_oauth_client、api-key 桥真实落 t_api_key、明文只出现一次、轮换带宽限期（真实 Tomcat + H2）
@SpringBootTest(classes = OpenModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenCredentialModuleTest {

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
        try {
            jdbc.execute("DELETE FROM t_api_key");
        } catch (org.springframework.dao.DataAccessException ignored) {
            // t_api_key 由 api-key 库建表，未装配时忽略
        }
        token = JsonPath.read(login(), "$.data");
    }

    @Test
    void webAppIssuesRealOauth2ClientAndRotates() throws Exception {
        String appId = approvedApp("网页凭证应用", "WEB");

        String issued = post("/open/portal/apps/" + appId + "/credential");
        String clientId = JsonPath.read(issued, "$.data.clientId").toString();
        String secret = JsonPath.read(issued, "$.data.clientSecret").toString();
        assertThat(clientId).startsWith("cli_");
        assertThat(secret).isNotBlank();
        assertThat(JsonPath.read(issued, "$.data.type").toString()).isEqualTo("OAUTH2");

        Integer clientRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_oauth_client WHERE client_id = ?", Integer.class, clientId);
        assertThat(clientRows).isEqualTo(1);
        String storedSecret = jdbc.queryForObject(
                "SELECT client_secret FROM t_oauth_client WHERE client_id = ?", String.class, clientId);
        assertThat(storedSecret).startsWith("ENC(");
        assertThat(storedSecret).isNotEqualTo(secret);

        HttpResponse<String> again = send("POST", "/open/portal/apps/" + appId + "/credential", null);
        assertThat(again.statusCode()).isEqualTo(400);
        assertThat(again.body()).contains("凭证已签发");

        String rotated = post("/open/portal/apps/" + appId + "/credential/rotate");
        assertThat(JsonPath.read(rotated, "$.data.clientSecret").toString()).isNotEqualTo(secret);
        assertThat(rotated).contains("previousSecretExpireAt");

        String viewed = get("/open/portal/apps/" + appId + "/credential");
        assertThat(JsonPath.read(viewed, "$.data.clientSecret").toString()).isEqualTo("******");
        String maskedInTable = jdbc.queryForObject(
                "SELECT client_secret FROM t_open_credential WHERE app_id = ?", String.class, appId);
        assertThat(maskedInTable).isEqualTo("******");
    }

    @Test
    void serverAppIssuesRealApiKey() throws Exception {
        String appId = approvedApp("服务端凭证应用", "SERVER");
        String issued = post("/open/portal/apps/" + appId + "/credential");
        assertThat(JsonPath.read(issued, "$.data.type").toString()).isEqualTo("API_KEY");
        String apiKey = JsonPath.read(issued, "$.data.clientId").toString();
        assertThat(apiKey).startsWith("sk_");

        Integer keyRows = jdbc.queryForObject("SELECT COUNT(*) FROM t_api_key WHERE api_key = ?",
                Integer.class, apiKey);
        assertThat(keyRows).isEqualTo(1);

        String rotated = post("/open/portal/apps/" + appId + "/credential/rotate");
        assertThat(JsonPath.read(rotated, "$.data.clientId").toString()).startsWith("sk_");
        assertThat(JsonPath.read(rotated, "$.data.clientId").toString()).isNotEqualTo(apiKey);
    }

    @Test
    void issueRequiresOnlineStatus() throws Exception {
        String appId = post("/open/portal/apps", "{\"name\":\"草稿凭证应用\",\"appType\":\"WEB\","
                + "\"redirectUris\":[\"https://cb.example.com/cb\"]}", "$.data.appId");
        HttpResponse<String> response = send("POST", "/open/portal/apps/" + appId + "/credential", null);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("仅已上线应用可签发凭证");
    }

    private String approvedApp(String name, String type) throws Exception {
        String appId = post("/open/portal/apps", "{\"name\":\"" + name + "\",\"appType\":\"" + type + "\","
                + "\"redirectUris\":[\"https://cb.example.com/cb\"]}", "$.data.appId");
        post("/open/portal/apps/" + appId + "/submit", null);
        post("/open/admin/apps/" + appId + "/approve", null);
        return appId;
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

    private String post(String path) throws Exception {
        return post(path, (String) null);
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
