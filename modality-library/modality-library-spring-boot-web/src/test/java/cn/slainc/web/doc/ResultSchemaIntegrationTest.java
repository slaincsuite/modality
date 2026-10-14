package cn.slainc.web.doc;

import cn.slainc.core.result.Result;
import cn.slainc.utils.json.JsonUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {ResultSchemaIntegrationTest.TestApplication.class, ResultSchemaIntegrationTest.ProbeController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResultSchemaIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @RestController
    static class ProbeController {

        // 返回 Object：springdoc 生成空响应结构，应补 ResultAny 引用
        @GetMapping(value = "/probe/plain", produces = MediaType.APPLICATION_JSON_VALUE)
        public Object plain() {
            return "ok";
        }

        // 返回 Result<String>：已有结构，不应被改写
        @GetMapping(value = "/probe/result", produces = MediaType.APPLICATION_JSON_VALUE)
        public Result<String> result() {
            return Result.ok("data");
        }

        // void + HttpServletResponse：流式导出，应标 binary
        @GetMapping("/probe/export")
        public void export(HttpServletResponse response) throws Exception {
            response.setContentType("application/octet-stream");
            response.getOutputStream().write(new byte[] {1, 2, 3});
        }

        // @NoWrap：协议裸响应，不应被改写
        @cn.slainc.web.result.NoWrap
        @GetMapping("/probe/nowrap")
        public Object nowrap() {
            return Map.of("raw", true);
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
    private Map<String, Object> docs() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs/default");
        assertEquals(200, response.statusCode(), response.body());
        return JsonUtils.parseMap(response.body());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> operationOf(Map<String, Object> document, String path) {
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> pathItem = (Map<String, Object>) paths.get(path);
        assertNotNull(pathItem, "缺少路径 " + path);
        return (Map<String, Object>) pathItem.get("get");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> okResponse(Map<String, Object> operation) {
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        return (Map<String, Object>) responses.get("200");
    }

    @Test
    void registersSharedResultAnySchema() throws Exception {
        Map<String, Object> document = docs();
        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        assertNotNull(schemas.get("ResultAny"), "components 应注册共享 ResultAny 模型");
    }

    @Test
    void fillsMissingResponseSchemaWithResultAny() throws Exception {
        Map<String, Object> document = docs();
        Map<String, Object> ok = okResponse(operationOf(document, "/probe/plain"));
        assertNotNull(ok, "缺响应结构的端点应补 200 响应");
        String json = JsonUtils.toJson(ok);
        assertTrue(json.contains("#/components/schemas/ResultAny"), json);
        assertTrue(json.contains("application/json"), json);
    }

    @Test
    void keepsTypedResultResponseUntouched() throws Exception {
        Map<String, Object> document = docs();
        Map<String, Object> ok = okResponse(operationOf(document, "/probe/result"));
        String json = JsonUtils.toJson(ok);
        assertTrue(json.contains("$ref"), "已定义的 Result 泛型响应应保留引用: " + json);
        assertFalse(json.contains("ResultAny"), json);
    }

    @Test
    void marksStreamingEndpointAsBinary() throws Exception {
        Map<String, Object> document = docs();
        Map<String, Object> ok = okResponse(operationOf(document, "/probe/export"));
        assertNotNull(ok, "流式导出端点应补 200 响应");
        String json = JsonUtils.toJson(ok);
        assertTrue(json.contains("application/octet-stream"), json);
        assertTrue(json.contains("binary"), json);
        assertFalse(json.contains("ResultAny"), json);
    }

    @Test
    void skipsNoWrapEndpoint() throws Exception {
        Map<String, Object> document = docs();
        Map<String, Object> ok = okResponse(operationOf(document, "/probe/nowrap"));
        String json = ok == null ? "" : JsonUtils.toJson(ok);
        assertFalse(json.contains("ResultAny"), "NoWrap 端点不应被包装: " + json);
    }
}
