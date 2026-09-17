package cn.slainc.sign;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.crypto.random.SecureRandomUtils;
import cn.slainc.sign.composer.SignResult;
import cn.slainc.sign.config.SignKeys;
import cn.slainc.sign.key.SignKeyDefinition;
import cn.slainc.sign.tool.SignTool;
import cn.slainc.utils.json.JsonUtils;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SignTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignHttpAcceptanceTest {

    private static final String HMAC_APP = "http-hmac-app";

    private static final String RSA_APP = "http-rsa-app";

    private static final String SM2_APP = "http-sm2-app";

    private static final String FIXED_APP = "fixed-app";

    @Autowired
    private SignTool signTool;

    @Autowired
    private DynamicConfigService configService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private HttpClient httpClient;

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5)).build();
        deleteByPrefix(SignKeys.PREFIX);
        registerKeys();
    }

    private void deleteByPrefix(String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private void registerKeys() {
        SignKeyDefinition hmacKey = SignTestSupport.hmacKey(HMAC_APP, SignAlgorithm.HMAC_SHA256, "http-secret-001");
        SignKeyDefinition fixedKey = SignTestSupport.hmacKey(FIXED_APP, SignAlgorithm.HMAC_SHA256, "fixed-secret-001");
        SignKeyDefinition rsaKey = SignTestSupport.rsaKey(RSA_APP);
        SignKeyDefinition sm2Key = SignTestSupport.sm2Key(SM2_APP);
        configService.set(SignKeys.KEYS_CONFIG_KEY,
                JsonUtils.toJson(List.of(hmacKey, fixedKey, rsaKey, sm2Key)));
    }

    private String query(Map<String, List<String>> params) {
        StringBuilder builder = new StringBuilder();
        params.forEach((name, values) -> {
            for (String value : values) {
                if (builder.length() > 0) {
                    builder.append('&');
                }
                builder.append(encode(name)).append('=').append(encode(value));
            }
        });
        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, List<String>> orderParams(String orderNo, String amount) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("orderNo", List.of(orderNo));
        params.put("amount", List.of(amount));
        return params;
    }

    private HttpResponse<String> getOrder(SignResult result, String appId, String algorithm,
                                          Map<String, List<String>> params) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/test/order?" + query(params)))
                .GET()
                .header("X-App-Id", appId)
                .header("X-Timestamp", result.timestamp())
                .header("X-Nonce", result.nonce())
                .header("X-Sign", result.signature());
        if (algorithm != null) {
            builder.header("X-Sign-Algorithm", algorithm);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postSubmit(SignResult result, Map<String, List<String>> params) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/test/submit"))
                .POST(HttpRequest.BodyPublishers.ofString(query(params)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Timestamp", result.timestamp())
                .header("X-Nonce", result.nonce())
                .header("X-Sign", result.signature())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertRejected(HttpResponse<String> response, String reasonFragment) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(400100);
        assertThat((String) JsonPath.read(response.body(), "$.message")).contains(reasonFragment);
    }

    @Test
    void correctlySignedRequestSucceeds() throws Exception {
        Map<String, List<String>> params = orderParams("A001", "100");
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, keyOf(HMAC_APP), 2, params);
        HttpResponse<String> response = getOrder(result, HMAC_APP, null, params);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.data.orderNo")).isEqualTo("A001");
    }

    @Test
    void replayedRequestIsBlocked() throws Exception {
        Map<String, List<String>> params = orderParams("A002", "200");
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, keyOf(HMAC_APP), 2, params);
        HttpResponse<String> first = getOrder(result, HMAC_APP, null, params);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        HttpResponse<String> replay = getOrder(result, HMAC_APP, null, params);
        assertRejected(replay, "重放");
    }

    @Test
    void tamperedParameterIsBlocked() throws Exception {
        Map<String, List<String>> signed = orderParams("A003", "300");
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, keyOf(HMAC_APP), 2, signed);
        HttpResponse<String> response = getOrder(result, HMAC_APP, null, orderParams("B003", "300"));
        assertRejected(response, "签名不匹配");
    }

    @Test
    void expiredTimestampIsBlocked() throws Exception {
        Map<String, List<String>> params = orderParams("A004", "400");
        String expired = String.valueOf(Instant.now().getEpochSecond() - 1000);
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, keyOf(HMAC_APP), 2, expired,
                SecureRandomUtils.nextHex(8), params, false);
        HttpResponse<String> response = getOrder(result, HMAC_APP, null, params);
        assertRejected(response, "时间戳");
    }

    @Test
    void rsaSignedRequestSucceeds() throws Exception {
        Map<String, List<String>> params = orderParams("A005", "500");
        SignResult result = signTool.sign(SignAlgorithm.RSA_SHA256, keyOf(RSA_APP), 2, params);
        HttpResponse<String> response = getOrder(result, RSA_APP, "RSA_SHA256", params);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
    }

    @Test
    void sm2SignedRequestSucceeds() throws Exception {
        Map<String, List<String>> params = orderParams("A006", "600");
        SignResult result = signTool.sign(SignAlgorithm.SM2, keyOf(SM2_APP), 2, params);
        HttpResponse<String> response = getOrder(result, SM2_APP, "SM2", params);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
    }

    @Test
    void missingSignHeaderIsRejected() throws Exception {
        Map<String, List<String>> params = orderParams("A007", "700");
        SignResult result = new SignResult("canonical", "", String.valueOf(Instant.now().getEpochSecond()),
                "nonce-0001");
        HttpResponse<String> response = getOrder(result, HMAC_APP, null, params);
        assertRejected(response, "缺少签名");
    }

    @Test
    void fixedAppV1SubmitWithExtendedWindowSucceeds() throws Exception {
        Map<String, List<String>> params = Map.of("orderNo", List.of("A008"));
        String timestamp = String.valueOf(Instant.now().getEpochSecond() - 400);
        SignResult result = signTool.sign(SignAlgorithm.HMAC_SHA256, keyOf(FIXED_APP), 1, timestamp,
                SecureRandomUtils.nextHex(8), params, false);
        HttpResponse<String> response = postSubmit(result, params);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((int) JsonPath.read(response.body(), "$.code")).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.data.orderNo")).isEqualTo("A008");
    }

    private SignKeyDefinition keyOf(String appId) {
        String json = configService.getString(SignKeys.KEYS_CONFIG_KEY, "[]");
        for (SignKeyDefinition key : JsonUtils.parseList(json, SignKeyDefinition.class)) {
            if (appId.equals(key.getAppId())) {
                return key;
            }
        }
        throw new IllegalStateException("测试密钥未登记：" + appId);
    }
}
