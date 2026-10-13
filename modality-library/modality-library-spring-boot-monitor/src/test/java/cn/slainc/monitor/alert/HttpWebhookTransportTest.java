package cn.slainc.monitor.alert;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpWebhookTransportTest {

    private HttpServer server;

    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    private final AtomicReference<String> receivedContentType = new AtomicReference<>();

    private final AtomicReference<String> receivedHeader = new AtomicReference<>();

    private volatile int status = 200;

    private volatile String response = "{\"errcode\":0}";

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedHeader.set(exchange.getRequestHeaders().getFirst("X-Trace"));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("真实 HTTP 推送：请求体、Content-Type 与自定义头都正确")
    void shouldPostJsonBody() {
        HttpWebhookTransport transport = new HttpWebhookTransport(Duration.ofSeconds(2), Duration.ofSeconds(2), true);
        String body = "{\"msgtype\":\"markdown\"}";
        String response = transport.post(url(), body, Map.of("X-Trace", "trace-1"));
        assertThat(receivedBody.get()).isEqualTo(body);
        assertThat(receivedContentType.get()).contains("application/json");
        assertThat(receivedHeader.get()).isEqualTo("trace-1");
        assertThat(response).isEqualTo("{\"errcode\":0}");
    }

    @Test
    @DisplayName("HTTP 非 2xx 时抛异常")
    void shouldFailOnHttpError() {
        status = 500;
        response = "boom";
        HttpWebhookTransport transport = new HttpWebhookTransport(Duration.ofSeconds(2), Duration.ofSeconds(2), true);
        assertThatThrownBy(() -> transport.post(url(), "{}", Map.of()))
                .isInstanceOf(RuntimeException.class);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
    }

    @Test
    @DisplayName("默认拒绝环回地址，不允许本机回调绕过校验")
    void shouldRejectLoopbackByDefault() {
        HttpWebhookTransport transport = new HttpWebhookTransport(Duration.ofSeconds(2), Duration.ofSeconds(2));
        assertThatThrownBy(() -> transport.post(url(), "{}", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内网");
    }
}
