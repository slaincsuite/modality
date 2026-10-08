package cn.slainc.web.integration;

import cn.slainc.core.exception.BizException;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.web.result.NoWrap;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {WebEndpointIntegrationTest.TestApplication.class,
        WebEndpointIntegrationTest.DemoController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebEndpointIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @RestController
    @RequestMapping("/demo")
    static class DemoController {

        @GetMapping("/ok")
        public Map<String, Object> ok() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", 1800000000000000001L);
            data.put("name", "modality");
            return data;
        }

        @GetMapping("/biz")
        public Map<String, Object> biz() {
            throw new BizException("下单失败");
        }

        @GetMapping("/boom")
        public Map<String, Object> boom() {
            throw new IllegalStateException("数据库连接失败");
        }

        @GetMapping("/raw")
        @NoWrap
        public String raw() {
            return "raw-text";
        }

        @GetMapping("/validate")
        public String validate(@RequestParam @Min(value = 1, message = "必须大于等于 1") int age) {
            return "age=" + age;
        }
    }

    @LocalServerPort
    int port;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(HttpResponse<String> response) {
        return JsonUtils.parseMap(response.body());
    }

    @Test
    void wrapsSuccessResponseIntoResult() throws Exception {
        HttpResponse<String> response = get("/demo/ok");
        assertEquals(200, response.statusCode());
        Map<String, Object> body = body(response);
        assertEquals(200, body.get("code"));
        assertEquals("成功", body.get("message"));
        assertNotNull(body.get("timestamp"));
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertEquals("modality", data.get("name"));
        assertEquals("1800000000000000001", data.get("id"));
    }

    @Test
    void returnsTraceIdInResponseHeader() throws Exception {
        HttpResponse<String> response = get("/demo/ok");
        String traceId = response.headers().firstValue("X-Trace-Id").orElse(null);
        assertNotNull(traceId);
        assertEquals(32, traceId.length());
    }

    @Test
    void propagatesUpstreamTraceId() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/demo/ok"))
                        .header("X-Trace-Id", "upstream-trace-001").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals("upstream-trace-001", response.headers().firstValue("X-Trace-Id").orElse(null));
    }

    @Test
    void mapsBusinessExceptionToUnifiedResult() throws Exception {
        HttpResponse<String> response = get("/demo/biz");
        assertEquals(400, response.statusCode());
        assertEquals(400100, body(response).get("code"));
        assertEquals("下单失败", body(response).get("message"));
    }

    @Test
    void returns500ForUnexpectedException() throws Exception {
        HttpResponse<String> response = get("/demo/boom");
        assertEquals(500, response.statusCode());
        assertEquals(500000, body(response).get("code"));
        assertEquals("服务器内部错误", body(response).get("message"));
    }

    @Test
    void noWrapSkipsResponseWrapping() throws Exception {
        HttpResponse<String> response = get("/demo/raw");
        assertEquals(200, response.statusCode());
        assertEquals("raw-text", response.body());
    }

    @Test
    void returnsUnifiedResultOnNotFound() throws Exception {
        HttpResponse<String> response = get("/not-exists-at-all");
        assertEquals(404, response.statusCode());
        assertEquals(404000, body(response).get("code"));
        assertEquals("请求的资源不存在", body(response).get("message"));
    }

    @Test
    void validatesRequestParameters() throws Exception {
        HttpResponse<String> response = get("/demo/validate?age=0");
        assertEquals(400, response.statusCode());
        assertTrue(String.valueOf(body(response).get("message")).contains("必须大于等于 1"), response.body());
    }

    @Test
    void appliesSecurityHeaders() throws Exception {
        HttpResponse<String> response = get("/demo/ok");
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertEquals("SAMEORIGIN", response.headers().firstValue("X-Frame-Options").orElse(null));
    }
}
