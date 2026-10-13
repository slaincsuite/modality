package cn.slainc.monitor;

import cn.slainc.monitor.alert.AlertEvaluationScheduler;
import cn.slainc.monitor.alert.AlertService;
import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.monitor.support.TestApplication;
import cn.slainc.utils.json.JsonUtils;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=monitor-acceptance",
                "management.endpoints.web.exposure.include=health,info,metrics,prometheus,modality",
                "management.endpoint.health.show-details=always",
                "spring.datasource.url=jdbc:h2:mem:monitor_acceptance;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.hikari.pool-name=acceptance-hikari",
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=6379",
                "spring.data.redis.password=${MODALITY_REDIS_PASSWORD:}",
                "modality.monitor.system.cache-millis=1000",
                "modality.monitor.redis.cache-millis=1000"})
class MonitorActuatorAcceptanceTest {

    private static final String REDIS_HOST = "127.0.0.1";

    private static final int REDIS_PORT = 6379;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private BusinessMetrics businessMetrics;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertEvaluationScheduler scheduler;

    @BeforeAll
    static void requireRedis() {
        Assumptions.assumeTrue(redisAvailable(), "本地 Redis 不可用，已跳过监控端到端验收");
    }

    @Test
    @DisplayName("/actuator/prometheus 返回 200 与 Prometheus 文本格式，且含自定义指标")
    void shouldExposePrometheusEndpoint() throws Exception {
        get("/actuator/health");
        businessMetrics.loginSuccess("web");
        businessMetrics.loginSuccess("web");
        businessMetrics.loginSuccess("web");
        businessMetrics.apiCall(true);
        businessMetrics.apiCall(false);

        HttpResponse<String> first = get("/actuator/prometheus");
        assertThat(first.statusCode()).isEqualTo(200);
        String contentType = first.headers().firstValue("Content-Type").orElse("");
        assertThat(contentType).startsWith("text/plain");
        String body = first.body();
        assertThat(body).contains("modality_system_cpu_usage");
        assertThat(body).contains("modality_system_memory_used_bytes");
        assertThat(body).contains("modality_http_requests_active");
        assertThat(body).contains("modality_business_login_total");
        assertThat(body).contains("result=\"success\"");
        assertThat(body).contains("modality_alert_evaluations_total");

        HttpResponse<String> second = get("/actuator/prometheus");
        String secondBody = second.body();
        assertThat(secondBody).contains("http_server_requests_seconds_bucket");
        assertThat(secondBody).contains("http_server_requests_seconds_count");
        assertThat(secondBody).contains("modality_system_disk_usage");
        assertThat(secondBody).contains("hikaricp_connections_active");
        assertThat(secondBody).contains("hikaricp_connections_idle");
        assertThat(secondBody).contains("hikaricp_connections_pending");
        assertThat(secondBody).contains("hikaricp_connections_max");
        assertThat(secondBody).contains("pool=\"acceptance-hikari\"");
        assertThat(secondBody).doesNotContain("modality_db_pool_active").doesNotContain("modality_db_pool_max");
        assertThat(secondBody).contains("modality_redis_memory_used_bytes");
        assertThat(secondBody).contains("modality_redis_keyspace_hits");
        assertThat(secondBody).contains("jvm_memory_used_bytes");
        assertThat(secondBody).contains("jvm_gc_pause_seconds");
        assertThat(secondBody).contains("jvm_threads_states_threads");
        assertThat(secondBody).contains("jvm_classes_loaded_classes");
        assertThat(secondBody).contains("jvm_buffer_memory_used_bytes");
        assertThat(secondBody).contains("jvm_threads_deadlocked");
    }

    @Test
    @DisplayName("健康检查与就绪/存活探针可访问")
    void shouldExposeHealthEndpoints() throws Exception {
        HttpResponse<String> health = get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
        HttpResponse<String> liveness = get("/actuator/health/liveness");
        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(liveness.body()).contains("\"status\":\"UP\"");
        HttpResponse<String> readiness = get("/actuator/health/readiness");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("/actuator/modality 返回版本、构建、启动时长与业务计数")
    void shouldExposeApplicationInfoEndpoint() throws Exception {
        businessMetrics.loginSuccess("endpoint");
        HttpResponse<String> response = get("/actuator/modality");
        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> body = JsonUtils.parseMap(response.body());
        assertThat(body).containsKeys("application", "build", "startup", "businessMetrics");
        @SuppressWarnings("unchecked")
        Map<String, Object> application = (Map<String, Object>) body.get("application");
        assertThat(application).containsEntry("name", "monitor-acceptance");
        assertThat(application).containsEntry("version", "unknown");
        @SuppressWarnings("unchecked")
        Map<String, Object> build = (Map<String, Object>) body.get("build");
        @SuppressWarnings("unchecked")
        Map<String, Object> git = (Map<String, Object>) build.get("git");
        assertThat(git).containsEntry("commit", "unknown").containsEntry("available", "false");
        @SuppressWarnings("unchecked")
        Map<String, Object> startup = (Map<String, Object>) body.get("startup");
        assertThat(startup.get("uptime")).isInstanceOf(String.class);
        assertThat(String.valueOf(startup.get("uptimeMillis"))).matches("[0-9]+");
        @SuppressWarnings("unchecked")
        Map<String, Object> business = (Map<String, Object>) body.get("businessMetrics");
        assertThat(Double.parseDouble(String.valueOf(business.get("loginSuccess")))).isPositive();
        assertThat(body.toString()).contains("monitor-acceptance");
    }

    @Test
    @DisplayName("BusinessMetrics 记录后注册表里的计数器确实增加")
    void shouldIncreaseCounters() {
        businessMetrics.loginFailure("app", "密码错误");
        double before = registry.get("modality.business.login").tag("result", "failure").tag("type", "app")
                .tag("reason", "密码错误").counter().count();
        businessMetrics.loginFailure("app", "密码错误");
        businessMetrics.loginFailure("app", "密码错误");
        double after = registry.get("modality.business.login").tag("result", "failure").tag("type", "app")
                .tag("reason", "密码错误").counter().count();
        assertThat(after - before).isEqualTo(2d);
    }

    @Test
    @DisplayName("4xx 响应计入 HTTP 错误计数")
    void shouldCountHttpErrors() throws Exception {
        double before = registry.get("modality.http.requests.errors").tag("type", "client").counter().count();
        get("/actuator/not-exists-endpoint");
        double after = registry.get("modality.http.requests.errors").tag("type", "client").counter().count();
        assertThat(after - before).isEqualTo(1d);
    }

    @Test
    @DisplayName("告警评估调度在真实应用里启动，默认规则已装载")
    void shouldRunAlertScheduler() {
        assertThat(scheduler.isRunning()).isTrue();
        assertThat(alertService.rules()).hasSize(5);
        scheduler.evaluateNow();
        scheduler.evaluateNow();
        assertThat(registry.get("modality.alert.evaluations").counter().count()).isGreaterThanOrEqualTo(2d);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static boolean redisAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(REDIS_HOST, REDIS_PORT), 1000);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
