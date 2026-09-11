package cn.slainc.captcha;

import cn.slainc.captcha.store.CaptchaStore;
import cn.slainc.captcha.support.CaptchaDataCodec;
import cn.slainc.captcha.support.CaptchaKeys;
import cn.slainc.core.config.DynamicConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CaptchaHttpEndToEndTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CaptchaStore captchaStore;

    @Autowired
    private DynamicConfigService dynamicConfigService;

    @BeforeEach
    void setUp() {
        CaptchaIntegrationSupport.clean(stringRedisTemplate, dynamicConfigService);
    }

    @AfterEach
    void tearDown() {
        for (String key : CaptchaIntegrationSupport.DYNAMIC_KEYS) {
            dynamicConfigService.remove(key);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> result = MAPPER.readValue(response.body(), Map.class);
        result.put("__status", response.statusCode());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(Map<String, Object> response) {
        return (Map<String, Object>) response.get("data");
    }

    private int statusOf(Map<String, Object> response) {
        return (Integer) response.get("__status");
    }

    private CaptchaData storedData(String channel, String id) {
        String json = captchaStore.get(CaptchaKeys.dataKey(channel, id));
        return json == null ? null : CaptchaDataCodec.read(json);
    }

    @Test
    void imageEndpointReturnsCompleteChallenge() throws Exception {
        Map<String, Object> response = post("/captcha/image",
                Map.of("channel", "WEB", "type", "CHAR_ALNUM", "clientIp", "203.0.115.1"));
        assertEquals(200, statusOf(response));
        assertEquals(200, response.get("code"));
        Map<String, Object> data = dataOf(response);
        assertNotNull(data.get("id"));
        assertEquals("WEB", data.get("channel"));
        assertEquals("CHAR_ALNUM", data.get("type"));
        assertTrue(String.valueOf(data.get("imageBase64")).startsWith("data:image/png;base64,"));
        assertEquals(130, data.get("width"));
        assertEquals(48, data.get("height"));
        assertEquals(300, data.get("ttlSeconds"));
    }

    @Test
    void challengeResponseNeverLeaksAnswerOrCoordinates() throws Exception {
        Map<String, Object> charResponse = post("/captcha/image",
                Map.of("channel", "WEB", "type", "CHAR_ALNUM", "clientIp", "203.0.115.2"));
        String raw = MAPPER.writeValueAsString(charResponse);
        assertFalse(raw.contains("\"answer\""));
        assertFalse(raw.contains("\"slideX\""));
        assertFalse(raw.contains("\"points\""));
        assertFalse(raw.contains("\"gapX\""));

        Map<String, Object> slideResponse = post("/captcha/image",
                Map.of("channel", "WEB", "type", "SLIDE", "clientIp", "203.0.115.2"));
        String slideRaw = MAPPER.writeValueAsString(slideResponse);
        assertFalse(slideRaw.contains("\"slideX\""));
        assertFalse(slideRaw.contains("\"gapX\""));
        assertFalse(slideRaw.contains("\"points\""));

        Map<String, Object> clickResponse = post("/captcha/image",
                Map.of("channel", "WEB", "type", "CLICK", "clientIp", "203.0.115.2"));
        String clickRaw = MAPPER.writeValueAsString(clickResponse);
        assertFalse(clickRaw.contains("\"points\""));
        assertFalse(clickRaw.contains("\"coordinates\""));
    }

    @Test
    void slideChallengeCarriesSliderImageOnly() throws Exception {
        Map<String, Object> response = post("/captcha/image",
                Map.of("channel", "WEB", "type", "SLIDE", "clientIp", "203.0.115.3"));
        Map<String, Object> data = dataOf(response);
        assertTrue(String.valueOf(data.get("imageBase64")).startsWith("data:image/png;base64,"));
        assertTrue(String.valueOf(data.get("sliderImageBase64")).startsWith("data:image/png;base64,"));
    }

    @Test
    void clickChallengeCarriesWordListOnly() throws Exception {
        Map<String, Object> response = post("/captcha/image",
                Map.of("channel", "WEB", "type", "CLICK", "clientIp", "203.0.115.4"));
        Map<String, Object> data = dataOf(response);
        List<String> words = (List<String>) data.get("words");
        assertTrue(words.size() == 3 || words.size() == 4);
    }

    @Test
    void charVerifyIsOneShotOverHttp() throws Exception {
        Map<String, Object> image = post("/captcha/image",
                Map.of("channel", "WEB", "type", "CHAR_ALNUM", "clientIp", "203.0.115.5"));
        String id = String.valueOf(dataOf(image).get("id"));
        String answer = storedData("WEB", id).answer();

        Map<String, Object> first = post("/captcha/verify",
                Map.of("channel", "WEB", "id", id, "code", answer));
        assertEquals(200, first.get("code"));
        assertEquals(Boolean.TRUE, dataOf(first).get("success"));
        assertEquals("验证通过", dataOf(first).get("message"));

        Map<String, Object> second = post("/captcha/verify",
                Map.of("channel", "WEB", "id", id, "code", answer));
        assertEquals(Boolean.FALSE, dataOf(second).get("success"));
        assertTrue(String.valueOf(dataOf(second).get("message")).contains("过期或不存在"));
    }

    @Test
    void slideVerifyIsOneShotOverHttp() throws Exception {
        Map<String, Object> image = post("/captcha/image",
                Map.of("channel", "WEB", "type", "SLIDE", "clientIp", "203.0.115.6"));
        String id = String.valueOf(dataOf(image).get("id"));
        int slideX = storedData("WEB", id).slideX();

        Map<String, Object> first = post("/captcha/verify",
                Map.of("channel", "WEB", "id", id, "slideX", slideX));
        assertEquals(Boolean.TRUE, dataOf(first).get("success"));

        Map<String, Object> second = post("/captcha/verify",
                Map.of("channel", "WEB", "id", id, "slideX", slideX));
        assertEquals(Boolean.FALSE, dataOf(second).get("success"));
    }

    @Test
    void unknownIdFailsWithChineseMessage() throws Exception {
        Map<String, Object> response = post("/captcha/verify",
                Map.of("channel", "WEB", "id", "ghost", "code", "0000"));
        assertEquals(Boolean.FALSE, dataOf(response).get("success"));
        assertTrue(String.valueOf(dataOf(response).get("message")).contains("过期或不存在"));
    }

    @Test
    void lockFlowOverHttp() throws Exception {
        dynamicConfigService.set(CaptchaService.CONFIG_FAIL_THRESHOLD, "2");
        try {
            Map<String, Object> image = post("/captcha/image",
                    Map.of("channel", "WEB", "type", "CHAR_ALNUM", "clientIp", "203.0.115.7"));
            String id = String.valueOf(dataOf(image).get("id"));
            post("/captcha/verify", Map.of("channel", "WEB", "id", id, "code", "错"));
            post("/captcha/verify", Map.of("channel", "WEB", "id", id, "code", "错"));
            Map<String, Object> locked = post("/captcha/verify",
                    Map.of("channel", "WEB", "id", id, "code", "任意"));
            assertEquals("验证码已锁定，请稍后重试", dataOf(locked).get("message"));
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_FAIL_THRESHOLD);
        }
    }

    @Test
    void rateLimitedIpIsRejectedOverHttp() throws Exception {
        dynamicConfigService.set(CaptchaService.CONFIG_IP_LIMIT_PER_MINUTE, "1");
        try {
            Map<String, Object> first = post("/captcha/image",
                    Map.of("channel", "WEB", "clientIp", "203.0.115.8"));
            assertEquals(200, first.get("code"));
            Map<String, Object> second = post("/captcha/image",
                    Map.of("channel", "WEB", "clientIp", "203.0.115.8"));
            assertEquals(429, statusOf(second));
            assertEquals(429000, second.get("code"));
            assertTrue(String.valueOf(second.get("message")).contains("频繁"));
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_IP_LIMIT_PER_MINUTE);
            CaptchaIntegrationSupport.deleteByPattern(stringRedisTemplate, "modality:ratelimit:*captcha*");
        }
    }

    @Test
    void blacklistedIpIsRejectedOverHttp() throws Exception {
        dynamicConfigService.set(CaptchaService.CONFIG_IP_BLACKLIST, "203.0.115.9");
        try {
            Map<String, Object> response = post("/captcha/image",
                    Map.of("channel", "WEB", "clientIp", "203.0.115.9"));
            assertEquals(403, statusOf(response));
            assertEquals(403000, response.get("code"));
            assertTrue(String.valueOf(response.get("message")).contains("黑名单"));
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_IP_BLACKLIST);
        }
    }
}
