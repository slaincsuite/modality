package cn.slainc.apikey;

import cn.dev33.satoken.apikey.model.ApiKeyModel;
import cn.slainc.apikey.service.ApiKeyAdminService;
import cn.slainc.apikey.service.ApiKeyStatsService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ApiKeyTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiKeyHttpAcceptanceTest {

    @Autowired
    private ApiKeyAdminService adminService;

    @Autowired
    private ApiKeyStatsService statsService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private HttpClient httpClient;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
        jdbcTemplate.update("DELETE FROM t_infra_api_key");
        deleteByPrefix("modality:apikey:");
        deleteByPrefix("satoken:");
    }

    @Test
    @DisplayName("有效 Key 且 Scope 匹配时请求通过")
    void validKeyWithScopePasses() throws Exception {
        String apiKey = issue("order:read");
        HttpResponse<String> response = get("/test/order-read", apiKey);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(200);
        assertThat((String) JsonPath.<String>read(response.body(), "$.data")).isEqualTo("order-read-ok");
    }

    @Test
    @DisplayName("Scope 不匹配时被拦截并返回中文提示")
    void scopeMismatchIsRejected() throws Exception {
        String apiKey = issue("order:read");
        HttpResponse<String> response = get("/test/order-write", apiKey);
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(401000);
        assertThat((String) JsonPath.<String>read(response.body(), "$.message")).contains("Scope");
    }

    @Test
    @DisplayName("OR 模式下具备任一 Scope 即通过")
    void orModeScopePasses() throws Exception {
        String apiKey = issue("order:admin");
        HttpResponse<String> response = get("/test/order-any", apiKey);
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(200);
    }

    @Test
    @DisplayName("无效 Key 被拦截")
    void invalidKeyIsRejected() throws Exception {
        HttpResponse<String> response = get("/test/order-read", "sk_does-not-exist-0000000000000000");
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(401000);
        assertThat((String) JsonPath.<String>read(response.body(), "$.message")).contains("无效");
    }

    @Test
    @DisplayName("缺少 Key 时被拦截")
    void missingKeyIsRejected() throws Exception {
        HttpResponse<String> response = get("/test/order-read", null);
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(401000);
    }

    @Test
    @DisplayName("停用后立即被拦截")
    void disabledKeyIsRejected() throws Exception {
        String apiKey = issue("order:read");
        assertThat((int) JsonPath.<Integer>read(get("/test/order-read", apiKey).body(), "$.code")).isEqualTo(200);
        adminService.disable(apiKey);
        HttpResponse<String> response = get("/test/order-read", apiKey);
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(401000);
        assertThat((String) JsonPath.<String>read(response.body(), "$.message")).contains("禁用");
    }

    @Test
    @DisplayName("重新启用后恢复访问")
    void reEnabledKeyWorksAgain() throws Exception {
        String apiKey = issue("order:read");
        adminService.disable(apiKey);
        adminService.enable(apiKey);
        HttpResponse<String> response = get("/test/order-read", apiKey);
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(200);
    }

    @Test
    @DisplayName("IP 白名单不匹配本机来源时被拦截")
    void ipWhitelistBlocksRequest() throws Exception {
        ApiKeyModel model = adminService.issue(2002L, "白名单", List.of("order:read"), 30L,
                Map.of("ipWhitelist", List.of("10.99.99.0/24")));
        HttpResponse<String> response = get("/test/order-read", model.getApiKey());
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(401000);
        assertThat((String) JsonPath.<String>read(response.body(), "$.message")).contains("白名单");
    }

    @Test
    @DisplayName("IP 白名单命中本机来源时放行")
    void ipWhitelistAllowsLoopback() throws Exception {
        ApiKeyModel model = adminService.issue(2003L, "白名单放行", List.of("order:read"), 30L,
                Map.of("ipWhitelist", List.of("127.0.0.1", "10.99.99.0/24")));
        HttpResponse<String> response = get("/test/order-read", model.getApiKey());
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(200);
    }

    @Test
    @DisplayName("QPS 限流生效，突发请求被部分拦截")
    void qpsLimitRejectsBurst() throws Exception {
        ApiKeyModel model = adminService.issue(2004L, "限流", List.of("order:read"), 30L,
                Map.of("qpsLimit", 1));
        int rejected = 0;
        int allowed = 0;
        for (int i = 0; i < 8; i++) {
            HttpResponse<String> response = get("/test/order-read", model.getApiKey());
            int code = JsonPath.<Integer>read(response.body(), "$.code");
            if (code == 200) {
                allowed++;
            } else {
                rejected++;
                assertThat((String) JsonPath.<String>read(response.body(), "$.message")).contains("频率");
            }
        }
        assertThat(allowed).isGreaterThanOrEqualTo(1);
        assertThat(rejected).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("使用统计计数器随请求增长且可直接从 Redis 读出")
    void usageStatsGrowInRedis() throws Exception {
        String apiKey = issue("order:read");
        for (int i = 0; i < 3; i++) {
            assertThat((int) JsonPath.<Integer>read(get("/test/order-read", apiKey).body(), "$.code"))
                    .isEqualTo(200);
        }
        String fingerprint = apiKey.length() > 8 ? apiKey.substring(0, 8) : apiKey;
        String total = redisTemplate.opsForValue().get("modality:apikey:stats:" + fingerprint + ":total");
        assertThat(total).isNotNull();
        assertThat(Long.parseLong(total)).isGreaterThanOrEqualTo(3);
        assertThat(statsService.total(apiKey)).isGreaterThanOrEqualTo(3);
        Map<String, Long> daily = statsService.daily(apiKey, 1);
        assertThat(daily.values().iterator().next()).isGreaterThanOrEqualTo(3);
        assertThat(statsService.lastUsed(apiKey)).isNotNull();
    }

    @Test
    @DisplayName("轮换后新 Key 可用，旧 Key 在宽限期内仍可用")
    void rotateKeepsOldKeyDuringGrace() throws Exception {
        String oldKey = issue("order:read");
        ApiKeyModel fresh = adminService.rotate(oldKey, 24);
        assertThat(fresh.getApiKey()).isNotEqualTo(oldKey);

        HttpResponse<String> oldResponse = get("/test/order-read", oldKey);
        assertThat((int) JsonPath.<Integer>read(oldResponse.body(), "$.code")).isEqualTo(200);

        HttpResponse<String> freshResponse = get("/test/order-read", fresh.getApiKey());
        assertThat((int) JsonPath.<Integer>read(freshResponse.body(), "$.code")).isEqualTo(200);

        List<ApiKeyRecord> records = adminService.listByLoginId(2001L);
        assertThat(records).hasSize(2);
    }

    @Test
    @DisplayName("宽限期归零的旧 Key 立即失效")
    void rotateWithZeroGraceExpiresOldKeyImmediately() throws Exception {
        String oldKey = issue("order:read");
        adminService.rotate(oldKey, 0);
        HttpResponse<String> response = get("/test/order-read", oldKey);
        assertThat((int) JsonPath.<Integer>read(response.body(), "$.code")).isEqualTo(401000);
    }

    @Test
    @DisplayName("延长有效期后 Key 仍可用且过期时间被推移")
    void extendPushesExpiry() {
        String apiKey = issue("order:read");
        ApiKeyRecord before = adminService.listByLoginId(2001L).stream()
                .filter(record -> apiKey.equals(record.getApiKey())).findFirst().orElseThrow();
        long now = System.currentTimeMillis();
        adminService.extend(apiKey, 10);
        ApiKeyRecord after = adminService.listByLoginId(2001L).stream()
                .filter(record -> apiKey.equals(record.getApiKey())).findFirst().orElseThrow();
        assertThat(after.getExpiresTime()).isGreaterThan(before.getExpiresTime());
        assertThat(after.getExpiresTime()).isGreaterThan(now + 9L * 86400000L);
    }

    @Test
    @DisplayName("CSV 导出包含签发的 Key 与表头")
    void csvExportContainsIssuedKeys() {
        ApiKeyModel first = adminService.issue(2001L, "导出一", List.of("order:read"), 30L, null);
        ApiKeyModel second = adminService.issue(2001L, "导出,带逗号", List.of("order:write"), 30L, null);
        String csv = adminService.exportCsv(2001L);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("名称,介绍,API Key,账号ID");
        assertThat(csv).contains(first.getApiKey());
        assertThat(csv).contains(second.getApiKey());
        assertThat(csv).contains("\"导出,带逗号\"");
    }

    @Test
    @DisplayName("批量签发返回多个不重复的 Key")
    void batchIssueReturnsDistinctKeys() {
        List<ApiKeyModel> keys = adminService.batchIssue(2005L, "批量", List.of("order:read"), 30L, null, 3);
        assertThat(keys).hasSize(3);
        assertThat(keys.stream().map(ApiKeyModel::getApiKey).distinct()).hasSize(3);
    }

    private String issue(String scope) {
        ApiKeyModel model = adminService.issue(2001L, "验收", List.of(scope), 30L, null);
        return model.getApiKey();
    }

    private HttpResponse<String> get(String path, String apiKey) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).GET();
        if (apiKey != null) {
            builder.header("X-API-Key", apiKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private void deleteByPrefix(String prefix) {
        var keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
