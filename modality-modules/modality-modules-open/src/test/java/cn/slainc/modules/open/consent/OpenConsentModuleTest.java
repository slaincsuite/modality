package cn.slainc.modules.open.consent;

import cn.slainc.modules.open.OpenModuleApplication;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

// 授权记录与事件桥：用户签约/一键解约、管理员强制与批量解约、多条件分页、core 事件转发（真实 Tomcat + H2）
@SpringBootTest(classes = {OpenModuleApplication.class, OpenConsentModuleTest.CollectorConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenConsentModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private CoreEventCollector coreEventCollector;

    @LocalServerPort
    private int port;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        for (String table : new String[]{"t_open_app", "t_open_scope", "t_open_consent", "t_open_credential",
                "t_open_app_log"}) {
            jdbc.execute("DELETE FROM " + table);
        }
        coreEventCollector.events.clear();
        token = JsonPath.read(login(), "$.data");
        post("/open/admin/scopes", "{\"name\":\"batch11_base\",\"title\":\"基础信息\","
                + "\"group\":\"基础\",\"riskLevel\":\"LOW\"}");
        post("/open/admin/scopes", "{\"name\":\"batch11_profile\",\"title\":\"个人资料\","
                + "\"group\":\"资料\",\"riskLevel\":\"HIGH\"}");
        String appId = post("/open/portal/apps", "{\"name\":\"授权目标应用\",\"appType\":\"WEB\","
                        + "\"redirectUris\":[\"https://cb.example.com/cb\"],\"scopes\":[\"batch11_base\"]}",
                "$.data.appId");
        post("/open/portal/apps/" + appId + "/submit", null);
        post("/open/admin/apps/" + appId + "/approve", null);
    }

    @Test
    void grantRevokeQueryAndExportLoop() throws Exception {
        String appId = currentAppId();
        post("/open/my/consents/" + appId, "{\"scopes\":[\"batch11_base\",\"batch11_profile\"]}");

        String mine = get("/open/my/consents");
        assertThat((Integer) JsonPath.read(mine, "$.data.length()")).isEqualTo(1);
        assertThat(mine).contains("batch11_profile");

        HttpResponse<String> scopeRevoke = send("DELETE", "/open/my/consents/" + appId + "/scopes/batch11_profile",
                null);
        assertThat(scopeRevoke.statusCode()).isEqualTo(200);
        assertThat(get("/open/my/consents")).doesNotContain("batch11_profile");

        String page = get("/open/admin/consents/page?active=true");
        assertThat(Integer.parseInt(JsonPath.read(page, "$.data.total").toString())).isEqualTo(1);

        HttpResponse<String> revoke = send("DELETE", "/open/my/consents/" + appId, null);
        assertThat(revoke.statusCode()).isEqualTo(200);
        assertThat(Integer.parseInt(JsonPath.read(get("/open/admin/consents/page?active=true"),
                "$.data.total").toString())).isZero();
        assertThat(Integer.parseInt(JsonPath.read(get("/open/admin/consents/page?active=false"),
                "$.data.total").toString())).isEqualTo(1);
    }

    @Test
    void adminForceAndBatchRevoke() throws Exception {
        String appId = currentAppId();
        for (long userId = 93011L; userId <= 93013L; userId++) {
            jdbc.update("INSERT INTO t_open_consent (id, user_id, app_id, scopes, granted_at, source)"
                    + " VALUES (?, ?, ?, ?, ?, 'PORTAL')", "cst_" + userId, String.valueOf(userId), appId,
                    "[\"batch11_base\"]", System.currentTimeMillis());
        }

        HttpResponse<String> force = send("DELETE", "/open/admin/consents/93011/" + appId, null);
        assertThat(force.statusCode()).isEqualTo(200);
        assertThat(Integer.parseInt(JsonPath.read(get("/open/admin/consents/page?active=true"),
                "$.data.total").toString())).isEqualTo(2);

        String batch = post("/open/admin/consents/" + appId + "/batch-revoke", null);
        assertThat(Integer.parseInt(JsonPath.read(batch, "$.data.revoked").toString())).isEqualTo(2);
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_open_consent WHERE app_id = ? AND revoked_at IS NULL", Integer.class, appId);
        assertThat(active).isZero();
    }

    @Test
    void openApiEventsAreForwardedToCoreBus() throws Exception {
        String appId = currentAppId();
        publisher.publishEvent(new cn.slainc.openapi.event.ConsentGrantedEvent("93001", appId,
                List.of("batch11_base"), System.currentTimeMillis()));
        publisher.publishEvent(new cn.slainc.openapi.event.ConsentRevokedEvent("93001", appId,
                System.currentTimeMillis()));

        assertThat(coreEventCollector.events).hasSize(2);
        assertThat(coreEventCollector.events.get(0)).isInstanceOf(cn.slainc.core.event.ConsentGrantedEvent.class);
        cn.slainc.core.event.ConsentGrantedEvent granted =
                (cn.slainc.core.event.ConsentGrantedEvent) coreEventCollector.events.get(0);
        assertThat(granted.getUserId()).isEqualTo(93001L);
        assertThat(granted.getClientId()).isEqualTo(appId);
        assertThat(granted.getScopes()).containsExactly("batch11_base");
        assertThat(coreEventCollector.events.get(1)).isInstanceOf(cn.slainc.core.event.ConsentRevokedEvent.class);
    }

    private String currentAppId() {
        return jdbc.queryForObject("SELECT app_id FROM t_open_app LIMIT 1", String.class);
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

    public static class CoreEventCollector {

        final List<Object> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void onCoreConsentGranted(cn.slainc.core.event.ConsentGrantedEvent event) {
            events.add(event);
        }

        @EventListener
        public void onCoreConsentRevoked(cn.slainc.core.event.ConsentRevokedEvent event) {
            events.add(event);
        }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class CollectorConfiguration {

        @Bean
        CoreEventCollector coreEventCollector() {
            return new CoreEventCollector();
        }
    }
}
