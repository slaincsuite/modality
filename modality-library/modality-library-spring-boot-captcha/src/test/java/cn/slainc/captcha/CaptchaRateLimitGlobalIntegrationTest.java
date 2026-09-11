package cn.slainc.captcha;

import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "modality.captcha.global-limit-per-minute=2")
class CaptchaRateLimitGlobalIntegrationTest {

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        CaptchaIntegrationSupport.clean(stringRedisTemplate, null);
    }

    @Test
    void globalLimitRejectsWhenExceeded() {
        assertNotNull(captchaService.generate("WEB", CaptchaType.CHAR_ALNUM, "198.51.100.1"));
        assertNotNull(captchaService.generate("WEB", CaptchaType.CHAR_ALNUM, "198.51.100.2"));
        BizException exception = assertThrows(BizException.class,
                () -> captchaService.generate("WEB", CaptchaType.CHAR_ALNUM, "198.51.100.3"));
        assertTrue(exception.getMessage().contains("过多"));
        assertEquals(429000, exception.code());
    }

    @Test
    void globalLimitIsSharedAcrossIpsAndChannels() {
        assertNotNull(captchaService.generate("WEB", CaptchaType.CHAR_ALNUM, "198.51.100.5"));
        assertNotNull(captchaService.generate("APP", CaptchaType.CHAR_ALNUM, "198.51.100.6"));
        BizException exception = assertThrows(BizException.class,
                () -> captchaService.generate("MINI", CaptchaType.CHAR_ALNUM, "198.51.100.7"));
        assertTrue(exception.getMessage().contains("过多"));
    }
}
