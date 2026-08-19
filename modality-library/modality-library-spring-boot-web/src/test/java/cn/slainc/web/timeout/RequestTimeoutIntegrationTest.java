package cn.slainc.web.timeout;

import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {RequestTimeoutIntegrationTest.TestApplication.class,
        RequestTimeoutIntegrationTest.SlowController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"modality.web.timeout.async-timeout=300ms", "modality.web.timeout.observe-timeout=100ms"})
class RequestTimeoutIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @RestController
    static class SlowController {

        @GetMapping("/slow")
        public String slow() throws InterruptedException {
            Thread.sleep(300);
            return "slow-done";
        }

        @GetMapping("/async")
        public Callable<String> async() {
            return () -> {
                Thread.sleep(2000);
                return "async-done";
            };
        }

        @GetMapping("/quick-async")
        public Callable<String> quickAsync() {
            return () -> "quick-done";
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    RequestTimeoutWatch watch;

    @Autowired
    WebServerApplicationContext context;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void timesOutAsyncRequestWithUnifiedResult() throws Exception {
        long before = watch.overdueTotal();
        HttpResponse<String> response = get("/async");

        assertEquals(503, response.statusCode(), response.body());
        Map<String, Object> body = JsonUtils.parseMap(response.body());
        assertEquals(503000, body.get("code"));
        assertEquals("请求处理超时", body.get("message"));
        assertTrue(watch.overdueTotal() > before, "异步超时请求也应被观测计数");
    }

    @Test
    void returnsNormallyWhenAsyncWithinTimeout() throws Exception {
        HttpResponse<String> response = get("/quick-async");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("quick-done"), response.body());
    }

    @Test
    void observesSlowSynchronousRequest() throws Exception {
        long before = watch.overdueTotal();
        HttpResponse<String> response = get("/slow");

        assertEquals(200, response.statusCode());
        assertTrue(watch.overdueTotal() > before, "超过观测阈值的同步请求应计数并告警");
        assertEquals(0, watch.inFlightCount());
    }

    @Test
    void appliesConnectionTimeoutToTomcatConnector() {
        TomcatWebServer webServer = (TomcatWebServer) context.getWebServer();
        assertEquals("20000", String.valueOf(webServer.getTomcat().getConnector().getProperty("connectionTimeout")));
    }
}
