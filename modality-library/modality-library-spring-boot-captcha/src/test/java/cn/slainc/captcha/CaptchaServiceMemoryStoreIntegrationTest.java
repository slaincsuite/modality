package cn.slainc.captcha;

import cn.slainc.captcha.CaptchaInput.ClickPoint;
import cn.slainc.captcha.store.CaptchaStore;
import cn.slainc.captcha.store.MemoryCaptchaStore;
import cn.slainc.captcha.support.CaptchaKeys;
import cn.slainc.core.config.DynamicConfigService;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "modality.captcha.store=memory")
class CaptchaServiceMemoryStoreIntegrationTest {

    private static final String CHANNEL = "WEB";

    private static final String IP = "203.0.114.20";

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private CaptchaStore captchaStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

    private CaptchaChallenge generate(CaptchaType type) {
        return captchaService.generate(CHANNEL, type, IP);
    }

    private CaptchaData storedData(String id) {
        return CaptchaIntegrationSupport.storedData(captchaStore, CHANNEL, id);
    }

    private CaptchaVerifyResult verify(String id, CaptchaInput input) {
        return captchaService.verify(CHANNEL, id, input);
    }

    private List<ClickPoint> toPoints(int[][] points) {
        List<ClickPoint> result = new ArrayList<>();
        for (int[] point : points) {
            result.add(new ClickPoint(point[0], point[1]));
        }
        return result;
    }

    @Test
    void memoryStoreBeanIsSelected() {
        assertInstanceOf(MemoryCaptchaStore.class, captchaStore);
    }

    @Test
    void charCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        String answer = storedData(challenge.id()).answer();
        assertTrue(verify(challenge.id(), CaptchaInput.ofCode(answer)).success());
        assertFalse(verify(challenge.id(), CaptchaInput.ofCode(answer)).success());
    }

    @Test
    void chineseCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.CHINESE);
        String answer = storedData(challenge.id()).answer();
        assertTrue(verify(challenge.id(), CaptchaInput.ofCode(answer)).success());
        assertFalse(verify(challenge.id(), CaptchaInput.ofCode(answer)).success());
    }

    @Test
    void arithmeticCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.ARITHMETIC);
        String answer = storedData(challenge.id()).answer();
        assertTrue(verify(challenge.id(), CaptchaInput.ofCode(answer)).success());
        assertFalse(verify(challenge.id(), CaptchaInput.ofCode(answer)).success());
    }

    @Test
    void slideCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.SLIDE);
        Integer slideX = storedData(challenge.id()).slideX();
        assertNotNull(slideX);
        assertTrue(verify(challenge.id(), CaptchaInput.ofSlide(slideX)).success());
        assertFalse(verify(challenge.id(), CaptchaInput.ofSlide(slideX)).success());
    }

    @Test
    void clickCaptchaIsOneShot() {
        CaptchaChallenge challenge = generate(CaptchaType.CLICK);
        int[][] points = storedData(challenge.id()).points();
        assertTrue(verify(challenge.id(), CaptchaInput.ofClick(toPoints(points))).success());
        assertFalse(verify(challenge.id(), CaptchaInput.ofClick(toPoints(points))).success());
    }

    @Test
    void threeFailuresLockTheCaptcha() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        for (int i = 0; i < 3; i++) {
            verify(challenge.id(), CaptchaInput.ofCode("错"));
        }
        assertTrue(captchaStore.isLocked(CaptchaKeys.lockKey(CHANNEL, challenge.id())));
        CaptchaVerifyResult result = verify(challenge.id(), CaptchaInput.ofCode("错"));
        assertEquals("验证码已锁定，请稍后重试", result.message());
    }

    @Test
    void failedAttemptKeepsChallengeUntilThreshold() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        String answer = storedData(challenge.id()).answer();
        assertFalse(verify(challenge.id(), CaptchaInput.ofCode("错")).success());
        assertNotNull(captchaStore.get(CaptchaKeys.dataKey(CHANNEL, challenge.id())), "未达阈值不应作废挑战");
        assertTrue(verify(challenge.id(), CaptchaInput.ofCode(answer)).success());
    }

    @Test
    void unknownIdFailsGracefully() {
        CaptchaVerifyResult result = verify("missing-id", CaptchaInput.ofCode("x"));
        assertFalse(result.success());
        assertTrue(result.message().contains("过期或不存在"));
    }

    @Test
    void channelsAreIsolated() {
        CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
        String answer = storedData(challenge.id()).answer();
        assertFalse(captchaService.verify("APP", challenge.id(), CaptchaInput.ofCode(answer)).success());
        assertTrue(verify(challenge.id(), CaptchaInput.ofCode(answer)).success());
    }

    @Test
    void expiredCaptchaFails() throws InterruptedException {
        dynamicConfigService.set(CaptchaService.CONFIG_TTL_SECONDS, "1");
        try {
            CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
            String answer = storedData(challenge.id()).answer();
            Thread.sleep(1300);
            CaptchaVerifyResult result = verify(challenge.id(), CaptchaInput.ofCode(answer));
            assertFalse(result.success());
            assertTrue(result.message().contains("过期或不存在"));
            assertNull(captchaStore.get(CaptchaKeys.dataKey(CHANNEL, challenge.id())));
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_TTL_SECONDS);
        }
    }

    @Test
    void dynamicFailThresholdLocksAfterSingleFailure() {
        dynamicConfigService.set(CaptchaService.CONFIG_FAIL_THRESHOLD, "1");
        try {
            CaptchaChallenge challenge = generate(CaptchaType.CHAR_ALNUM);
            verify(challenge.id(), CaptchaInput.ofCode("错"));
            CaptchaVerifyResult result = verify(challenge.id(), CaptchaInput.ofCode("错"));
            assertEquals("验证码已锁定，请稍后重试", result.message());
        } finally {
            dynamicConfigService.remove(CaptchaService.CONFIG_FAIL_THRESHOLD);
        }
    }
}
