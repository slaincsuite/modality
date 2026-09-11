package cn.slainc.captcha;

import cn.slainc.captcha.CaptchaInput.ClickPoint;
import cn.slainc.captcha.store.CaptchaStore;
import cn.slainc.captcha.support.CaptchaKeys;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CaptchaServiceRedisIntegrationTest {

    private static final String CHANNEL = "WEB";

    private static final String IP = "203.0.113.10";

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private CaptchaStore captchaStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private DynamicConfigService dynamicConfigService;

    @Autowired
    private CaptchaMetrics captchaMetrics;

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

    private CaptchaChallenge generate(CaptchaType type) {
        return captchaService.generate(CHANNEL, type, IP);
    }

    private CaptchaData storedData(String id) {
        return CaptchaIntegrationSupport.storedData(captchaStore, CHANNEL, id);
    }

    private CaptchaVerifyResult verify(String id, CaptchaInput input) {
        return captchaService.verify(CHANNEL, id, input);
    }

    private CaptchaVerifyResult verifyCode(String id, String code) {
        return verify(id, CaptchaInput.ofCode(code));
    }

    private List<ClickPoint> toPoints(int[][] points) {
        List<ClickPoint> result = new ArrayList<>();
        for (int[] point : points) {
            result.add(new ClickPoint(point[0], point[1]));
        }
        return result;
    }

    private List<ClickPoint> reversed(List<ClickPoint> points) {
        return points.stream().collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toList(), list -> {
                    java.util.Collections.reverse(list);
                    return list;
                }));
    }

    @Test
    void charCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        CaptchaData data = storedData(challenge.id());
        assertNotNull(data);
        assertEquals(CaptchaType.CHAR_ALNUM, data.type());
        assertTrue(verifyCode(challenge.id(), data.answer()).success());
        CaptchaVerifyResult second = verifyCode(challenge.id(), data.answer());
        assertFalse(second.success());
        assertTrue(second.message().contains("过期或不存在"));
    }

    @Test
    void numberCharCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_NUMBER);
        CaptchaData data = storedData(challenge.id());
        assertTrue(verifyCode(challenge.id(), data.answer()).success());
        assertFalse(verifyCode(challenge.id(), data.answer()).success());
    }

    @Test
    void alphaCharCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALPHA);
        CaptchaData data = storedData(challenge.id());
        assertTrue(verifyCode(challenge.id(), data.answer()).success());
        assertFalse(verifyCode(challenge.id(), data.answer()).success());
    }

    @Test
    void chineseCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.CHINESE);
        CaptchaData data = storedData(challenge.id());
        assertTrue(verifyCode(challenge.id(), data.answer()).success());
        assertFalse(verifyCode(challenge.id(), data.answer()).success());
    }

    @Test
    void arithmeticCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.ARITHMETIC);
        CaptchaData data = storedData(challenge.id());
        assertNotNull(data.answer());
        assertTrue(verifyCode(challenge.id(), data.answer()).success());
        assertFalse(verifyCode(challenge.id(), data.answer()).success());
    }

    @Test
    void slideCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.SLIDE);
        CaptchaData data = storedData(challenge.id());
        assertNotNull(data.slideX());
        assertTrue(verify(challenge.id(), CaptchaInput.ofSlide(data.slideX())).success());
        assertFalse(verify(challenge.id(), CaptchaInput.ofSlide(data.slideX())).success());
    }

    @Test
    void clickCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.CLICK);
        CaptchaData data = storedData(challenge.id());
        assertTrue(verify(challenge.id(), CaptchaInput.ofClick(toPoints(data.points()))).success());
        assertFalse(verify(challenge.id(), CaptchaInput.ofClick(toPoints(data.points()))).success());
    }

    @Test
    void charCaptchaIgnoresCaseAndWhitespace() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        String answer = storedData(challenge.id()).answer();
        assertTrue(verifyCode(challenge.id(), "  " + answer.toUpperCase() + " ").success());
    }

    @Test
    void chineseCaptchaTrimsWhitespace() {
        CaptchaChallenge challenge = generate(CaptchaType.CHINESE);
        String answer = storedData(challenge.id()).answer();
        assertTrue(verifyCode(challenge.id(), " " + answer + "\t").success());
    }

    @Test
    void arithmeticCaptchaTrimsWhitespace() {
        CaptchaChallenge challenge = generate(CaptchaType.ARITHMETIC);
        String answer = storedData(challenge.id()).answer();
        assertTrue(verifyCode(challenge.id(), " " + answer + " ").success());
    }

    @Test
    void slideBeyondToleranceFails() {
        CaptchaChallenge challenge = generate(CaptchaType.SLIDE);
        CaptchaData data = storedData(challenge.id());
        CaptchaVerifyResult result = verify(challenge.id(),
                CaptchaInput.ofSlide(data.slideX() + data.tolerance() + 4));
        assertFalse(result.success());
        assertTrue(result.message().contains("滑动位置不正确"));
    }

    @Test
    void slideWithinTolerancePasses() {
        CaptchaChallenge challenge = generate(CaptchaType.SLIDE);
        CaptchaData data = storedData(challenge.id());
        assertTrue(verify(challenge.id(), CaptchaInput.ofSlide(data.slideX() + data.tolerance())).success());
    }

    @Test
    void clickWrongOrderFails() {
        CaptchaChallenge challenge = generate(CaptchaType.CLICK);
        CaptchaData data = storedData(challenge.id());
        CaptchaVerifyResult result = verify(challenge.id(), CaptchaInput.ofClick(reversed(toPoints(data.points()))));
        assertFalse(result.success());
        assertTrue(result.message().contains("点选位置或顺序不正确"));
    }

    @Test
    void clickOffByMoreThanToleranceFails() {
        CaptchaChallenge challenge = generate(CaptchaType.CLICK);
        CaptchaData data = storedData(challenge.id());
        List<ClickPoint> shifted = toPoints(data.points()).stream()
                .map(point -> new ClickPoint(point.x() + data.tolerance() + 20, point.y()))
                .toList();
        assertFalse(verify(challenge.id(), CaptchaInput.ofClick(shifted)).success());
    }

    @Test
    void failedVerifyDeletesDataImmediately() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        verifyCode(challenge.id(), "错误答案");
        assertNull(captchaStore.get(CaptchaKeys.dataKey(CHANNEL, challenge.id())));
    }

    @Test
    void threeFailuresLockTheCaptcha() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        verifyCode(challenge.id(), "错");
        verifyCode(challenge.id(), "错");
        verifyCode(challenge.id(), "错");
        assertTrue(captchaStore.isLocked(CaptchaKeys.lockKey(CHANNEL, challenge.id())));
        CaptchaVerifyResult result = verifyCode(challenge.id(), "任意");
        assertFalse(result.success());
        assertEquals("验证码已锁定，请稍后重试", result.message());
    }

    @Test
    void lockedCaptchaRejectsEvenCorrectAnswer() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        String answer = storedData(challenge.id()).answer();
        dynamicConfigService.set(CaptchaService.CONFIG_FAIL_THRESHOLD, "1");
        try {
            verifyCode(challenge.id(), "错");
            CaptchaVerifyResult result = verifyCode(challenge.id(), answer);
            assertFalse(result.success());
            assertEquals("验证码已锁定，请稍后重试", result.message());
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_FAIL_THRESHOLD);
        }
    }

    @Test
    void lockedIdDoesNotAffectNewIds() {
        CaptchaChallenge locked = generate(CaptchaType.CHAR_ALNUM);
        verifyCode(locked.id(), "错");
        verifyCode(locked.id(), "错");
        verifyCode(locked.id(), "错");
        assertTrue(captchaStore.isLocked(CaptchaKeys.lockKey(CHANNEL, locked.id())));

        CaptchaChallenge fresh = generate(CaptchaType.CHAR_ALNUM);
        assertTrue(verifyCode(fresh.id(), storedData(fresh.id()).answer()).success());
    }

    @Test
    void unknownIdFailsGracefully() {
        CaptchaVerifyResult result = verifyCode("no-such-id", "1234");
        assertFalse(result.success());
        assertTrue(result.message().contains("过期或不存在"));
    }

    @Test
    void channelsAreIsolated() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        String answer = storedData(challenge.id()).answer();
        CaptchaVerifyResult crossChannel = captchaService.verify("APP", challenge.id(), CaptchaInput.ofCode(answer));
        assertFalse(crossChannel.success());
        assertTrue(verifyCode(challenge.id(), answer).success());
    }

    @Test
    void expiredCaptchaFails() throws InterruptedException {
        dynamicConfigService.set(CaptchaService.CONFIG_TTL_SECONDS, "1");
        try {
            CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
            String answer = storedData(challenge.id()).answer();
            Thread.sleep(1300);
            CaptchaVerifyResult result = verifyCode(challenge.id(), answer);
            assertFalse(result.success());
            assertTrue(result.message().contains("过期或不存在"));
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_TTL_SECONDS);
        }
    }

    @Test
    void dynamicTtlIsReflectedInChallenge() {
        dynamicConfigService.set(CaptchaService.CONFIG_TTL_SECONDS, "7");
        try {
            CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
            assertEquals(7L, challenge.ttlSeconds());
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_TTL_SECONDS);
        }
    }

    @Test
    void dynamicTypeHotSwitchChangesDefaultType() {
        dynamicConfigService.set(CaptchaService.CONFIG_TYPE, "SLIDE");
        try {
            CaptchaChallenge challenge = captchaService.generate(CHANNEL, IP);
            assertEquals(CaptchaType.SLIDE, challenge.type());
            assertNotNull(challenge.sliderImageBase64());
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_TYPE);
        }
    }

    @Test
    void dynamicLengthAppliesToNewChallenges() {
        dynamicConfigService.set(CaptchaService.CONFIG_LENGTH, "6");
        try {
            CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
            assertEquals(6, storedData(challenge.id()).answer().length());
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_LENGTH);
        }
    }

    @Test
    void dynamicIpLimitRejectsExcessGenerate() {
        dynamicConfigService.set(CaptchaService.CONFIG_IP_LIMIT_PER_MINUTE, "1");
        try {
            String ip = "198.51.100.77";
            captchaService.generate(CHANNEL, CaptchaType.CHAR_ALNUM, ip);
            BizException exception = assertThrows(BizException.class,
                    () -> captchaService.generate(CHANNEL, CaptchaType.CHAR_ALNUM, ip));
            assertTrue(exception.getMessage().contains("频繁"));
            assertEquals(429000, exception.code());
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_IP_LIMIT_PER_MINUTE);
            CaptchaIntegrationSupport.deleteByPattern(stringRedisTemplate, "modality:ratelimit:*captcha*");
        }
    }

    @Test
    void blacklistRejectsConfiguredIp() {
        dynamicConfigService.set(CaptchaService.CONFIG_IP_BLACKLIST, "203.0.113.55, 10.0.0.1");
        try {
            BizException exception = assertThrows(BizException.class,
                    () -> captchaService.generate(CHANNEL, CaptchaType.CHAR_ALNUM, "203.0.113.55"));
            assertTrue(exception.getMessage().contains("黑名单"));
            assertEquals(403000, exception.code());
            assertNotNull(captchaService.generate(CHANNEL, CaptchaType.CHAR_ALNUM, "203.0.113.56"));
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_IP_BLACKLIST);
        }
    }

    @Test
    void metricsCountersTrackOperations() {
        CaptchaMetricsSnapshot before = captchaMetrics.snapshot();
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        verifyCode(challenge.id(), "错");
        CaptchaChallenge another = generate(CaptchaType.CHAR_ALNUM);
        assertTrue(verifyCode(another.id(), storedData(another.id()).answer()).success());
        CaptchaMetricsSnapshot after = captchaMetrics.snapshot();
        assertEquals(before.generated() + 2, after.generated());
        assertEquals(before.verifySuccess() + 1, after.verifySuccess());
        assertEquals(before.verifyFailure() + 1, after.verifyFailure());
    }

    @Test
    void lockedMetricIncrementsWhenLockedVerifyHits() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        verifyCode(challenge.id(), "错");
        verifyCode(challenge.id(), "错");
        verifyCode(challenge.id(), "错");
        CaptchaMetricsSnapshot before = captchaMetrics.snapshot();
        verifyCode(challenge.id(), "错");
        assertEquals(before.locked() + 1, captchaMetrics.snapshot().locked());
    }

    @Test
    void nullInputCountsAsFailure() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        CaptchaVerifyResult result = verify(challenge.id(), null);
        assertFalse(result.success());
        assertEquals("验证码错误", result.message());
        assertNull(captchaStore.get(CaptchaKeys.dataKey(CHANNEL, challenge.id())));
    }

    @Test
    void blankChannelRejectedOnGenerate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> captchaService.generate(" ", CaptchaType.CHAR_ALNUM, IP));
        assertTrue(exception.getMessage().contains("渠道"));
    }

    @Test
    void verifyWithBlankChannelReturnsFailure() {
        CaptchaVerifyResult result = captchaService.verify("", "any-id", CaptchaInput.ofCode("x"));
        assertFalse(result.success());
        assertTrue(result.message().contains("渠道"));
    }

    @Test
    void verifyWithBlankIdReturnsFailure() {
        CaptchaVerifyResult result = verifyCode("", "1234");
        assertFalse(result.success());
        assertEquals("验证码标识不能为空", result.message());
    }

    @Test
    void generatedImageIsRealPng() throws Exception {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        String base64 = challenge.imageBase64().substring("data:image/png;base64,".length());
        byte[] bytes = java.util.Base64.getDecoder().decode(base64);
        assertEquals(0x89, bytes[0] & 0xFF);
        assertEquals('P', bytes[1]);
        assertEquals('N', bytes[2]);
        assertEquals('G', bytes[3]);
    }

    @Test
    void generatedImageHasConfiguredDimensions() throws Exception {
        CaptchaChallenge challenge = generate(CaptchaType.SLIDE);
        assertEquals(280, challenge.width());
        assertEquals(150, challenge.height());
        byte[] bytes = java.util.Base64.getDecoder()
                .decode(challenge.imageBase64().substring("data:image/png;base64,".length()));
        javax.imageio.ImageIO.setUseCache(false);
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        assertEquals(280, image.getWidth());
        assertEquals(150, image.getHeight());
    }
}
