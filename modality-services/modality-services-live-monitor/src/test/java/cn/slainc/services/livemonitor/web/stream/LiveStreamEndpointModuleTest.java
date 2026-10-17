package cn.slainc.services.livemonitor.web.stream;

import cn.slainc.services.livemonitor.LiveMonitorKeys;
import cn.slainc.services.livemonitor.LiveMonitorModuleApplication;
import cn.slainc.services.livemonitor.service.stream.LiveStreamProbe;
import cn.slainc.services.livemonitor.service.stream.StreamProbeResult;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = LiveMonitorModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiveStreamEndpointModuleTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private LiveStreamProbe liveStreamProbe;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.update("DELETE FROM t_live_stream");
        HttpResponse<String> login = send(null, null, "GET",
                "/live-monitor-test/login?userId=" + LiveMonitorModuleApplication.FULL_PERMISSION_USER_ID, null);
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        token = JsonPath.read(login.body(), "$.data");
    }

    @Test
    void shouldPageFilterAndReportStats() throws Exception {
        long onlineId = create(1L, "主舞台", "https://8.8.8.8/main.m3u8", true);
        long offlineId = create(1L, "备用流", "https://1.1.1.1/backup.m3u8", true);
        create(1L, "停用流", "https://9.9.9.9/disabled.m3u8", false);
        jdbc.update("UPDATE t_live_stream SET status = 'UP', last_latency_ms = 10 WHERE id = ?", onlineId);
        jdbc.update("UPDATE t_live_stream SET status = 'DOWN', last_latency_ms = 30 WHERE id = ?", offlineId);

        String byKeyword = get("/live-monitor/streams/page?current=1&size=10&keyword="
                + URLEncoder.encode("主舞台", StandardCharsets.UTF_8));
        assertThat(number(byKeyword, "$.data.total")).isEqualTo(1L);
        assertThat(JsonPath.read(byKeyword, "$.data.records[0].id").toString())
                .isEqualTo(String.valueOf(onlineId));

        String byStatus = get("/live-monitor/streams/page?current=1&size=10&status=up");
        assertThat(number(byStatus, "$.data.total")).isEqualTo(1L);

        String byEnabled = get("/live-monitor/streams/page?current=1&size=10&enabled=false");
        assertThat(number(byEnabled, "$.data.total")).isEqualTo(1L);

        String stats = get("/live-monitor/stats");
        assertThat(number(stats, "$.data.total")).isEqualTo(3L);
        assertThat(number(stats, "$.data.online")).isEqualTo(1L);
        assertThat(number(stats, "$.data.offline")).isEqualTo(1L);
        assertThat(number(stats, "$.data.unknown")).isEqualTo(1L);
        assertThat(number(stats, "$.data.averageLatencyMs")).isEqualTo(20L);
    }

    @Test
    void shouldCompleteCrudAndUseLogicalDelete() throws Exception {
        long id = create(1L, "主舞台", "https://8.8.8.8/main.m3u8", true);

        HttpResponse<String> update = postRaw(token, 1L, "PUT", "/live-monitor/streams/" + id,
                requestBody("主舞台-改", "https://8.8.8.8/main.m3u8", false));
        assertThat(update.statusCode()).as(update.body()).isEqualTo(200);
        assertThat(JsonPath.read(update.body(), "$.data.name").toString()).isEqualTo("主舞台-改");
        assertThat((Boolean) JsonPath.read(update.body(), "$.data.enabled")).isFalse();
        assertThat(JsonPath.read(update.body(), "$.data.status").toString()).isEqualTo(LiveMonitorKeys.STATUS_UNKNOWN);

        String page = get("/live-monitor/streams/page?current=1&size=10&keyword="
                + URLEncoder.encode("主舞台-改", StandardCharsets.UTF_8));
        assertThat(number(page, "$.data.total")).isEqualTo(1L);

        HttpResponse<String> delete = postRaw(token, 1L, "DELETE", "/live-monitor/streams/" + id, null);
        assertThat(delete.statusCode()).as(delete.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT deleted FROM t_live_stream WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(number(get("/live-monitor/streams/page?current=1&size=10"), "$.data.total")).isZero();
    }

    @Test
    void shouldPersistProbeResultsAndCheckOnlyEnabledStreams() throws Exception {
        String upUrl = "https://8.8.8.8/ok.m3u8";
        String downUrl = "https://1.1.1.1/fail.m3u8";
        String disabledUrl = "https://9.9.9.9/disabled.m3u8";
        long upId = create(1L, "正常流", upUrl, true);
        long downId = create(1L, "异常流", downUrl, true);
        create(1L, "停用流", disabledUrl, false);
        when(liveStreamProbe.probe(upUrl))
                .thenReturn(new StreamProbeResult(LiveMonitorKeys.STATUS_UP, 12L, null));
        when(liveStreamProbe.probe(downUrl))
                .thenReturn(new StreamProbeResult(LiveMonitorKeys.STATUS_DOWN, 45L,
                        "HTTP 状态码 500，直播源不可用"));

        HttpResponse<String> up = postRaw(token, 1L, "POST", "/live-monitor/streams/" + upId + "/check", null);
        assertThat(up.statusCode()).as(up.body()).isEqualTo(200);
        assertThat(JsonPath.read(up.body(), "$.data.status").toString()).isEqualTo(LiveMonitorKeys.STATUS_UP);
        assertThat(JsonPath.read(up.body(), "$.data.lastLatencyMs").toString()).isEqualTo("12");
        assertThat(JsonPath.read(up.body(), "$.data.lastCheckAt").toString()).isNotBlank();

        HttpResponse<String> down = postRaw(token, 1L, "POST", "/live-monitor/streams/" + downId + "/check", null);
        assertThat(down.statusCode()).as(down.body()).isEqualTo(200);
        assertThat(JsonPath.read(down.body(), "$.data.status").toString()).isEqualTo(LiveMonitorKeys.STATUS_DOWN);
        assertThat(JsonPath.read(down.body(), "$.data.lastError").toString()).contains("500");

        HttpResponse<String> checkAll = postRaw(token, 1L, "POST", "/live-monitor/streams/check-all", null);
        assertThat(checkAll.statusCode()).as(checkAll.body()).isEqualTo(200);
        assertThat(number(checkAll.body(), "$.data.online")).isEqualTo(1L);
        assertThat(number(checkAll.body(), "$.data.offline")).isEqualTo(1L);
        assertThat(number(checkAll.body(), "$.data.unknown")).isEqualTo(1L);
        assertThat(number(checkAll.body(), "$.data.averageLatencyMs")).isEqualTo(29L);
        verify(liveStreamProbe, never()).probe(disabledUrl);
    }

    @Test
    void shouldClearStaleProbeFieldsWhenStreamRecoversOrTargetChanges() throws Exception {
        String url = "https://8.8.8.8/recovery.m3u8";
        long id = create(1L, "恢复流", url, true);
        when(liveStreamProbe.probe(url))
                .thenReturn(new StreamProbeResult(LiveMonitorKeys.STATUS_DOWN, 20L,
                        "HTTP 状态码 500，直播源不可用"))
                .thenReturn(new StreamProbeResult(LiveMonitorKeys.STATUS_UP, 8L, null));

        HttpResponse<String> failed = postRaw(token, 1L, "POST", "/live-monitor/streams/" + id + "/check", null);
        assertThat(JsonPath.read(failed.body(), "$.data.lastError").toString()).contains("500");

        HttpResponse<String> recovered = postRaw(token, 1L, "POST", "/live-monitor/streams/" + id + "/check", null);
        assertThat((Object) JsonPath.read(recovered.body(), "$.data.lastError")).isNull();
        assertThat(JsonPath.read(recovered.body(), "$.data.status").toString()).isEqualTo(LiveMonitorKeys.STATUS_UP);

        HttpResponse<String> updated = postRaw(token, 1L, "PUT", "/live-monitor/streams/" + id,
                requestBody("恢复流-换址", "https://9.9.9.10/new.m3u8", true));
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
        assertThat(JsonPath.read(updated.body(), "$.data.status").toString()).isEqualTo(LiveMonitorKeys.STATUS_UNKNOWN);
        assertThat((Object) JsonPath.read(updated.body(), "$.data.lastCheckAt")).isNull();
        assertThat((Object) JsonPath.read(updated.body(), "$.data.lastLatencyMs")).isNull();
        assertThat((Object) JsonPath.read(updated.body(), "$.data.lastError")).isNull();
    }

    @Test
    void shouldRejectUnauthenticatedForbiddenAndInternalTargets() throws Exception {
        HttpResponse<String> anonymous = send(null, 1L, "GET", "/live-monitor/streams/page", null);
        assertThat(anonymous.statusCode()).isEqualTo(401);

        HttpResponse<String> login = send(null, null, "GET",
                "/live-monitor-test/login?userId=" + LiveMonitorModuleApplication.NO_PERMISSION_USER_ID, null);
        String noPermissionToken = JsonPath.read(login.body(), "$.data");
        HttpResponse<String> forbidden = send(noPermissionToken, 1L, "GET", "/live-monitor/streams/page", null);
        assertThat(forbidden.statusCode()).isEqualTo(403);

        HttpResponse<String> internal = postRaw(token, 1L, "POST", "/live-monitor/streams",
                requestBody("内网源", "http://169.254.169.254/latest/meta-data", true));
        assertThat(internal.statusCode()).as(internal.body()).isEqualTo(400);
        assertThat(JsonPath.read(internal.body(), "$.message").toString()).contains("内网");
    }

    @Test
    void shouldIsolateTenantsAndBlockCrossTenantWrites() throws Exception {
        long tenantOneId = create(1L, "租户一源", "https://8.8.8.8/tenant-one.m3u8", true);
        long tenantTwoId = create(2L, "租户二源", "https://1.1.1.1/tenant-two.m3u8", true);

        String tenantOnePage = send(token, 1L, "GET", "/live-monitor/streams/page", null).body();
        assertThat(number(tenantOnePage, "$.data.total")).isEqualTo(1L);
        assertThat(JsonPath.read(tenantOnePage, "$.data.records[0].name").toString()).isEqualTo("租户一源");

        HttpResponse<String> crossUpdate = postRaw(token, 2L, "PUT", "/live-monitor/streams/" + tenantOneId,
                requestBody("越权修改", "https://8.8.8.8/tenant-one.m3u8", false));
        assertThat(crossUpdate.statusCode()).as(crossUpdate.body()).isEqualTo(400);
        HttpResponse<String> crossDelete = postRaw(token, 2L, "DELETE",
                "/live-monitor/streams/" + tenantOneId, null);
        assertThat(crossDelete.statusCode()).as(crossDelete.body()).isEqualTo(400);
        HttpResponse<String> crossCheck = postRaw(token, 2L, "POST",
                "/live-monitor/streams/" + tenantOneId + "/check", null);
        assertThat(crossCheck.statusCode()).as(crossCheck.body()).isEqualTo(400);

        assertThat(jdbc.queryForObject("SELECT name FROM t_live_stream WHERE id = ?", String.class, tenantOneId))
                .isEqualTo("租户一源");
        assertThat(jdbc.queryForObject("SELECT deleted FROM t_live_stream WHERE id = ?", Integer.class,
                tenantOneId)).isZero();
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM t_live_stream WHERE id = ?", Long.class,
                tenantTwoId)).isEqualTo(2L);
        assertThat(number(send(token, 2L, "GET", "/live-monitor/streams/page", null).body(),
                "$.data.total")).isEqualTo(1L);
    }

    private long create(long tenantId, String name, String url, boolean enabled) throws Exception {
        HttpResponse<String> response = postRaw(token, tenantId, "POST", "/live-monitor/streams",
                requestBody(name, url, enabled));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return Long.parseLong(JsonPath.read(response.body(), "$.data.id").toString());
    }

    private String requestBody(String name, String url, boolean enabled) {
        return "{\"name\":\"" + name + "\",\"url\":\"" + url + "\",\"enabled\":" + enabled + "}";
    }

    private String get(String path) throws Exception {
        HttpResponse<String> response = send(token, 1L, "GET", path, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response.body();
    }

    private HttpResponse<String> postRaw(String satoken, long tenantId, String method, String path, String body)
            throws Exception {
        return send(satoken, tenantId, method, path, body);
    }

    private HttpResponse<String> send(String satoken, Long tenantId, String method, String path, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-Login-Type", "TENANT_USER");
        if (satoken != null) {
            builder.header("satoken", satoken);
        }
        if (tenantId != null) {
            builder.header("X-Tenant-Id", String.valueOf(tenantId));
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static long number(String json, String path) {
        Object value = JsonPath.read(json, path);
        return ((Number) value).longValue();
    }
}
