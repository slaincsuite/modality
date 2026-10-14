package cn.slainc.modules.infra;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InfraModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class InfraModuleTestBase {

    // 每个测试 JVM 使用独立的 Lucene 索引目录，避免上一次运行留下的索引影响断言
    private static final String SEARCH_INDEX_DIR;

    static {
        try {
            SEARCH_INDEX_DIR = java.nio.file.Files.createTempDirectory("modality-infra-search-")
                    .toAbsolutePath().toString();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @org.springframework.test.context.DynamicPropertySource
    static void searchProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("modality.search.directory", () -> SEARCH_INDEX_DIR);
        registry.add("modality.search.refresh-interval-ms", () -> "200");
    }

    protected static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    protected JdbcTemplate jdbc;

    protected String token;

    @BeforeEach
    void loginAndReset() throws Exception {
        for (String table : new String[]{"t_infra_config_history", "t_infra_dict_type", "t_infra_dict_item",
                "t_infra_notice", "t_infra_notify_template", "t_infra_notify_message", "t_infra_notify_webhook",
                "t_infra_notify_record", "t_infra_file_record", "t_search_document", "t_search_index_term",
                "t_search_history", "t_recycle_item"}) {
            jdbc.execute("DELETE FROM " + table);
        }
        jdbc.execute("DELETE FROM t_dynamic_config");
        token = JsonPath.read(raw("GET", "/infra-test/login", null), "$.data");
    }

    protected String raw(String method, String path, String body) throws Exception {
        return send(method, path, body).body();
    }

    protected HttpResponse<String> send(String method, String path, String body) throws Exception {
        return sendAs(token, method, path, body);
    }

    protected HttpResponse<String> sendAs(String satoken, String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port() + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("X-Login-Type", "TENANT_USER");
        if (satoken != null) {
            builder.header("satoken", satoken);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected String loginAs(long userId) throws Exception {
        return JsonPath.read(raw("GET", "/infra-test/login?userId=" + userId, null), "$.data");
    }

    protected String get(String path) throws Exception {
        return send("GET", path, null).body();
    }

    protected String post(String path, String body) throws Exception {
        HttpResponse<String> response = send("POST", path, body);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return response.body();
    }

    protected String post(String path, String body, String jsonPath) throws Exception {
        return JsonPath.read(post(path, body), jsonPath).toString();
    }

    protected HttpResponse<byte[]> sendBytes(String method, String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port() + path))
                .timeout(Duration.ofSeconds(30))
                .header("X-Login-Type", "TENANT_USER");
        if (token != null) {
            builder.header("satoken", token);
        }
        builder.method(method, HttpRequest.BodyPublishers.noBody());
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    protected HttpResponse<String> upload(String path, String fileField, String filename, byte[] content,
                                          String contentType, Map<String, String> extraFields) throws Exception {
        String boundary = "infra-" + UUID.randomUUID();
        StringBuilder head = new StringBuilder();
        List<byte[]> parts = new ArrayList<>();
        Map<String, String> fields = extraFields == null ? Map.of() : extraFields;
        for (Map.Entry<String, String> field : fields.entrySet()) {
            head.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(field.getKey()).append("\"\r\n\r\n")
                    .append(field.getValue()).append("\r\n");
        }
        parts.add(head.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder fileHead = new StringBuilder();
        fileHead.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(fileField).append("\"; filename=\"")
                .append(filename).append("\"\r\n")
                .append("Content-Type: ").append(contentType).append("\r\n\r\n");
        parts.add(fileHead.toString().getBytes(StandardCharsets.UTF_8));
        parts.add(content);
        parts.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] body = concat(parts);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port() + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Login-Type", "TENANT_USER")
                .header("satoken", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> uploadMulti(String path, Map<String, byte[]> filesByName, String contentType,
                                               Map<String, String> extraFields) throws Exception {
        String boundary = "infra-" + UUID.randomUUID();
        List<byte[]> parts = new ArrayList<>();
        StringBuilder head = new StringBuilder();
        Map<String, String> fields = extraFields == null ? Map.of() : extraFields;
        for (Map.Entry<String, String> field : fields.entrySet()) {
            head.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(field.getKey()).append("\"\r\n\r\n")
                    .append(field.getValue()).append("\r\n");
        }
        parts.add(head.toString().getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, byte[]> file : filesByName.entrySet()) {
            StringBuilder fileHead = new StringBuilder();
            fileHead.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"files\"; filename=\"")
                    .append(file.getKey()).append("\"\r\n")
                    .append("Content-Type: ").append(contentType).append("\r\n\r\n");
            parts.add(fileHead.toString().getBytes(StandardCharsets.UTF_8));
            parts.add(file.getValue());
            parts.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port() + path))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Login-Type", "TENANT_USER")
                .header("satoken", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(concat(parts)))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected String md5Hex(byte[] data) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("MD5").digest(data);
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    protected Map<String, String> fields(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static byte[] concat(List<byte[]> parts) {
        int total = parts.stream().mapToInt(part -> part.length).sum();
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    @org.springframework.boot.test.web.server.LocalServerPort
    private int serverPort;

    protected int port() {
        return serverPort;
    }
}
