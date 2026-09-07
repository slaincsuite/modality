package cn.slainc.boot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class Batch3InfrastructureAcceptanceTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("/actuator/prometheus 可访问且输出 Prometheus 文本格式")
    void prometheusEndpointExposed() throws Exception {
        HttpResponse<String> response = get("/actuator/prometheus");
        assertEquals(200, response.statusCode(), "Prometheus 端点不可访问: " + response.body());
        String body = response.body();
        assertTrue(body.contains("# HELP") || body.contains("# TYPE"),
                "响应不是 Prometheus 文本格式，前 500 字符: " + body.substring(0, Math.min(500, body.length())));
        assertTrue(body.contains("jvm_memory_used_bytes"), "缺少 JVM 指标");
        assertTrue(body.contains("http_server_requests"), "缺少 HTTP 指标");
    }

    @Test
    @DisplayName("/actuator/modality 返回应用版本与启动信息")
    void modalityInfoEndpointExposed() throws Exception {
        HttpResponse<String> response = get("/actuator/modality");
        assertEquals(200, response.statusCode(), "业务信息端点不可访问: " + response.body());
        assertTrue(response.body().contains("modality"), "缺少应用信息: " + response.body());
    }

    @Test
    @DisplayName("健康检查与就绪/存活探针可访问")
    void healthEndpointsExposed() throws Exception {
        assertEquals(200, get("/actuator/health/liveness").statusCode());
        assertEquals(200, get("/actuator/health/readiness").statusCode());
        assertTrue(get("/actuator/health").statusCode() == 200 || get("/actuator/health").statusCode() == 503);
    }
}
