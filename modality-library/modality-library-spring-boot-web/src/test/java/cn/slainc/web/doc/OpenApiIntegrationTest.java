package cn.slainc.web.doc;

import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {OpenApiIntegrationTest.TestApplication.class, OpenApiIntegrationTest.OrderController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    enum OrderStatus {
        PAID, SHIPPED, FINISHED
    }

    record DemoOrder(String code, OrderStatus status) {
    }

    @RestController
    static class OrderController {

        @GetMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
        public DemoOrder orders(@RequestParam("status") OrderStatus status) {
            return new DemoOrder("A-100", status);
        }
    }

    @LocalServerPort
    int port;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void exposesApiDocs() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertEquals(200, response.statusCode(), response.body());
        Map<String, Object> document = JsonUtils.parseMap(response.body());
        assertTrue(String.valueOf(document.get("openapi")).startsWith("3."), response.body());
        assertNotNull(document.get("paths"));
        assertTrue(response.body().contains("Modality API"), response.body());
    }

    @Test
    void exposesSecuritySchemeAndGroup() throws Exception {
        HttpResponse<String> grouped = get("/v3/api-docs/default");

        assertEquals(200, grouped.statusCode(), grouped.body());
        assertTrue(grouped.body().contains("bearerAuth"), "缺少 JWT Bearer 安全方案");
        assertTrue(grouped.body().contains("Modality API"), "缺少文档标题");
        assertTrue(grouped.body().contains("/orders"), "分组内应包含业务接口");

        HttpResponse<String> config = get("/v3/api-docs/swagger-config");
        assertEquals(200, config.statusCode(), config.body());
        assertTrue(config.body().contains("\"name\":\"default\""), config.body());
        assertTrue(config.body().contains("/v3/api-docs/default"), config.body());
    }

    @Test
    void rendersEnumValuesInSchema() throws Exception {
        HttpResponse<String> grouped = get("/v3/api-docs/default");

        assertEquals(200, grouped.statusCode(), grouped.body());
        assertTrue(grouped.body().contains("可选值：PAID、SHIPPED、FINISHED"), grouped.body());
    }

    @Test
    void listsUnifiedErrorCodes() throws Exception {
        HttpResponse<String> grouped = get("/v3/api-docs/default");

        assertEquals(200, grouped.statusCode(), grouped.body());
        assertTrue(grouped.body().contains("统一错误码"), grouped.body());
        assertTrue(grouped.body().contains("413000"), grouped.body());
        assertTrue(grouped.body().contains("503000"), grouped.body());
    }
}
