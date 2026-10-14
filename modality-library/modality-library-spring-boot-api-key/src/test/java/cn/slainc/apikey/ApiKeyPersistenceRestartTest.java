package cn.slainc.apikey;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.slainc.apikey.loader.JdbcApiKeyDataLoader;
import cn.slainc.apikey.admin.ApiKeyAdminService;
import cn.slainc.apikey.template.GuardedApiKeyTemplate;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyPersistenceRestartTest {

    private static final Path DB_DIR =
            Paths.get(System.getProperty("java.io.tmpdir"), "modality-apikey-persist");

    @BeforeAll
    static void cleanDatabaseFiles() throws IOException {
        deleteRecursively(DB_DIR);
    }

    @Test
    @DisplayName("应用重启后 API Key 不丢失，注解端点凭库内 Key 通过鉴权")
    void issuedKeySurvivesApplicationRestart() throws Exception {
        String apiKey;
        try (ConfigurableApplicationContext first = startApp()) {
            ApiKeyAdminService admin = first.getBean(ApiKeyAdminService.class);
            ApiKeyModel model = admin.issue(9001L, "重启验证", List.of("order:read"), 30L, null);
            apiKey = model.getApiKey();
            assertThat(apiKey).startsWith("sk_");
        }

        try (ConfigurableApplicationContext second = startApp()) {
            StringRedisTemplate redis = second.getBean(StringRedisTemplate.class);
            deleteByPrefix(redis, "satoken:apikey:");

            JdbcApiKeyDataLoader loader = second.getBean(JdbcApiKeyDataLoader.class);
            ApiKeyModel fromDatabase = loader.getApiKeyModelFromDatabase("apikey", apiKey);
            assertThat(fromDatabase).isNotNull();
            assertThat(fromDatabase.getLoginId()).isEqualTo("9001");
            assertThat(fromDatabase.getScopes()).containsExactly("order:read");

            GuardedApiKeyTemplate template = second.getBean(GuardedApiKeyTemplate.class);
            assertThat(template.getApiKeyModelFromCache(apiKey)).isNull();
            assertThat(template.getApiKey(apiKey)).isNotNull();

            int port = ((WebServerApplicationContext) second).getWebServer().getPort();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).build()
                    .send(HttpRequest.newBuilder()
                                    .uri(URI.create("http://127.0.0.1:" + port + "/test/order-read"))
                                    .header("X-API-Key", apiKey).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(200);
        }
    }

    private ConfigurableApplicationContext startApp() {
        String url = "jdbc:h2:file:" + DB_DIR.resolve("apikey").toAbsolutePath().toString().replace('\\', '/')
                + ";MODE=MySQL;AUTO_SERVER=FALSE";
        return new SpringApplicationBuilder(ApiKeyTestApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0", "--spring.datasource.dynamic.datasource.master.url=" + url);
    }

    private static void deleteByPrefix(StringRedisTemplate redisTemplate, String prefix) {
        var keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new IllegalStateException("清理 H2 持久化目录失败: " + path, e);
                }
            });
        }
    }
}
