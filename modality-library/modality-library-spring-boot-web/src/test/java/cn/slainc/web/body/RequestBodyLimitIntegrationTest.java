package cn.slainc.web.body;

import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = {RequestBodyLimitIntegrationTest.TestApplication.class,
        RequestBodyLimitIntegrationTest.UploadController.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"modality.web.request-body.max-request-size=4KB",
                "modality.web.request-body.max-file-size=1KB",
                "modality.web.request-body.max-form-post-size=1KB",
                "modality.web.request-body.max-swallow-size=64KB"})
class RequestBodyLimitIntegrationTest {

    private static final String BOUNDARY = "----modalityBoundary";

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @RestController
    static class UploadController {

        @PostMapping("/echo")
        public Map<String, Object> echo(@RequestBody String body) {
            return Map.of("length", body.length());
        }

        @PostMapping("/upload")
        public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
            return Map.of("size", file.getSize());
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    MultipartProperties multipartProperties;

    @Autowired
    TomcatServerProperties tomcatServerProperties;

    @Autowired
    WebServerApplicationContext context;

    @Test
    void returnsUnifiedResultWhenBodyExceedsLimit() throws Exception {
        HttpResponse<String> response = post("/echo", MediaType.TEXT_PLAIN_VALUE, new byte[8192]);
        assertEquals(413, response.statusCode());
        Map<String, Object> body = JsonUtils.parseMap(response.body());
        assertEquals(413000, body.get("code"));
        assertEquals("请求体过大", body.get("message"));
    }

    @Test
    void acceptsMultipartWithinLimit() throws Exception {
        HttpResponse<String> response = post("/upload", multipartContentType(), multipartBody("file", "a.txt", 512));
        assertEquals(200, response.statusCode(), response.body());
        Map<?, ?> data = (Map<?, ?>) JsonUtils.parseMap(response.body()).get("data");
        assertEquals("512", String.valueOf(data.get("size")));
    }

    @Test
    void returnsUnifiedResultWhenMultipartExceedsLimit() throws Exception {
        HttpResponse<String> response = post("/upload", multipartContentType(), multipartBody("file", "big.txt", 8192));
        assertEquals(413, response.statusCode(), response.body());
        assertEquals(413000, JsonUtils.parseMap(response.body()).get("code"));
    }

    @Test
    void returnsUnifiedResultWhenMultipartFileExceedsLimit() throws Exception {
        HttpResponse<String> response = post("/upload", multipartContentType(), multipartBody("file", "big.txt", 2048));
        assertEquals(413, response.statusCode(), response.body());
        assertEquals(413000, JsonUtils.parseMap(response.body()).get("code"));
    }

    @Test
    void appliesConfiguredLimitsToBootAndContainer() {
        assertEquals(4096L, multipartProperties.getMaxRequestSize().toBytes());
        assertEquals(1024L, multipartProperties.getMaxFileSize().toBytes());
        assertEquals(1024L, tomcatServerProperties.getMaxHttpFormPostSize().toBytes());
        assertEquals(65536L, tomcatServerProperties.getMaxSwallowSize().toBytes());
        TomcatWebServer webServer = (TomcatWebServer) context.getWebServer();
        assertEquals(1024, webServer.getTomcat().getConnector().getMaxPostSize());
        assertEquals("65536", String.valueOf(webServer.getTomcat().getConnector().getProperty("maxSwallowSize")));
    }

    private String multipartContentType() {
        return "multipart/form-data; boundary=" + BOUNDARY;
    }

    private HttpResponse<String> post(String path, String contentType, byte[] body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private byte[] multipartBody(String field, String filename, int size) {
        byte[] content = new byte[size];
        Arrays.fill(content, (byte) 'a');
        byte[] head = ("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: text/plain\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(content, 0, result, head.length, content.length);
        System.arraycopy(tail, 0, result, head.length + content.length, tail.length);
        return result;
    }
}
