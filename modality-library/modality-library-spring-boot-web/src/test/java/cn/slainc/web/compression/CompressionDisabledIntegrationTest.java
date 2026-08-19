package cn.slainc.web.compression;

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
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(classes = {CompressionDisabledIntegrationTest.TestApplication.class,
        CompressionDisabledIntegrationTest.PayloadController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "modality.web.compression.enabled=false")
class CompressionDisabledIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @RestController
    static class PayloadController {

        @GetMapping(value = "/payload", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> payload() {
            return Map.of("text", "压缩验证文本".repeat(64));
        }
    }

    @LocalServerPort
    int port;

    @Test
    void skipsCompressionWhenDisabled() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payload"))
                        .header("Accept-Encoding", "gzip, br").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertNull(response.headers().firstValue("Content-Encoding").orElse(null));
        assertEquals("成功", cn.slainc.utils.json.JsonUtils.parseMap(response.body()).get("message"));
    }
}
