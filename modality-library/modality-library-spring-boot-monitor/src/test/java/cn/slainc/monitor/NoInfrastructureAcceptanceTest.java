package cn.slainc.monitor;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.monitor.support.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.application.name=monitor-no-infrastructure",
                "management.endpoints.web.exposure.include=health,prometheus,modality",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisMonitorAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisConfigAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisCacheAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisLockAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisPubSubAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisRateLimitAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisStructureAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisSaTokenAutoConfiguration,"
                        + "cn.slainc.redis.autoconfigure.RedisJdbcConfigAutoConfiguration"})
class NoInfrastructureAcceptanceTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("没有 DataSource、没有 Redis 时应用照样启动，监控能力可用")
    void shouldStartWithoutInfrastructure() {
        assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(RedisConnectionFactory.class)).isEmpty();
        assertThat(context.getBean(MonitorProperties.class)).isNotNull();
        assertThat(context.getBean(BusinessMetrics.class)).isNotNull();
        assertThat(context.getBeanNamesForType(HealthIndicator.class)).isNotEmpty();
    }

    @Test
    @DisplayName("无基础设施时健康检查与 Prometheus 端点仍可用，且不注册连接池/Redis 指标")
    void shouldExposeEndpointsWithoutInfrastructure() throws Exception {
        HttpResponse<String> health = get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
        get("/actuator/prometheus");
        HttpResponse<String> prometheus = get("/actuator/prometheus");
        assertThat(prometheus.statusCode()).isEqualTo(200);
        String body = prometheus.body();
        assertThat(body).contains("modality_system_cpu_usage").contains("modality_business_login_total")
                .doesNotContain("modality_db_pool").doesNotContain("modality_redis_");
        HttpResponse<String> info = get("/actuator/modality");
        assertThat(info.statusCode()).isEqualTo(200);
        assertThat(info.body()).contains("monitor-no-infrastructure");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
