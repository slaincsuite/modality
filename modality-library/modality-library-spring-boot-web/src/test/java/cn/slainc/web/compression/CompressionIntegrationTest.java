package cn.slainc.web.compression;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {CompressionIntegrationTest.TestApplication.class,
        CompressionIntegrationTest.PayloadController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "modality.web.compression.min-response-size=1B")
class CompressionIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @RestController
    static class PayloadController {

        @GetMapping(value = "/payload", produces = MediaType.APPLICATION_JSON_VALUE)
        public Map<String, Object> payload() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", 1800000000000000001L);
            data.put("text", "压缩验证文本".repeat(32));
            return data;
        }
    }

    @LocalServerPort
    int port;

    private HttpResponse<byte[]> get(String acceptEncoding) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payload")).GET();
        if (acceptEncoding != null) {
            builder.header("Accept-Encoding", acceptEncoding);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private String text(HttpResponse<byte[]> response) {
        return normalize(new String(response.body(), StandardCharsets.UTF_8));
    }

    private String normalize(String json) {
        return json.replaceAll("\"timestamp\":\\d+", "\"timestamp\":0");
    }

    // brotli4j 是 compileOnly + testRuntimeOnly，测试内用反射解码以保持依赖作用域不变
    private byte[] brotliDecode(byte[] compressed) throws Exception {
        Class<?> decoder = Class.forName("com.aayushatharva.brotli4j.decoder.Decoder");
        Object direct = decoder.getMethod("decompress", byte[].class).invoke(null, (Object) compressed);
        return (byte[]) direct.getClass().getMethod("getDecompressedData").invoke(direct);
    }

    @Test
    void compressesWithGzipByDefault() throws Exception {
        HttpResponse<byte[]> plain = get(null);
        assertEquals(200, plain.statusCode());
        assertNull(plain.headers().firstValue("Content-Encoding").orElse(null));

        HttpResponse<byte[]> compressed = get("gzip");
        assertEquals(200, compressed.statusCode());
        assertEquals("gzip", compressed.headers().firstValue("Content-Encoding").orElse(null));
        try (GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(compressed.body()))) {
            assertEquals(text(plain), normalize(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    @Test
    void compressesWithBrotliAndDecodesToOriginal() throws Exception {
        HttpResponse<byte[]> plain = get(null);
        HttpResponse<byte[]> response = get("br");
        assertEquals(200, response.statusCode());
        assertEquals("br", response.headers().firstValue("Content-Encoding").orElse(null));
        assertNotNull(response.headers().firstValue("Vary").orElse(null));
        assertTrue(response.body().length < plain.body().length, "Brotli 压缩后应更小");
        assertEquals(text(plain), normalize(new String(brotliDecode(response.body()), StandardCharsets.UTF_8)));
    }

    @Test
    void prefersBrotliWhenClientAcceptsBoth() throws Exception {
        HttpResponse<byte[]> response = get("gzip, br");
        assertEquals(200, response.statusCode());
        assertEquals("br", response.headers().firstValue("Content-Encoding").orElse(null));
        assertTrue(normalize(new String(brotliDecode(response.body()), StandardCharsets.UTF_8))
                .contains("压缩验证文本"));
    }

    @Test
    void ignoresBrotliWhenQualityIsZero() throws Exception {
        HttpResponse<byte[]> response = get("br;q=0, gzip");
        assertEquals("gzip", response.headers().firstValue("Content-Encoding").orElse(null));
    }
}
