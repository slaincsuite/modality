package cn.slainc.web.doc;

import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = {OpenApiDisabledIntegrationTest.TestApplication.class,
        OpenApiDisabledIntegrationTest.DemoController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "modality.web.open-api.enabled=false")
class OpenApiDisabledIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @RestController
    static class DemoController {

        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }

    @LocalServerPort
    int port;

    @Test
    void closesApiDocsWhenDisabled() throws Exception {
        HttpResponse<String> docs = get("/v3/api-docs");

        assertEquals(404, docs.statusCode(), docs.body());
        Map<String, Object> body = JsonUtils.parseMap(docs.body());
        assertEquals(404000, body.get("code"));
        assertEquals("接口文档已关闭", body.get("message"));

        assertEquals(404, get("/swagger-ui/index.html").statusCode());
        assertEquals(200, get("/ping").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
