package cn.slainc.captcha.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaptchaKeysTest {

    @Test
    void dataKeyFollowsConvention() {
        assertEquals("modality:captcha:data:WEB:abc123", CaptchaKeys.dataKey("WEB", "abc123"));
    }

    @Test
    void failAndLockKeysFollowConvention() {
        assertEquals("modality:captcha:fail:APP:id1", CaptchaKeys.failKey("APP", "id1"));
        assertEquals("modality:captcha:lock:APP:id1", CaptchaKeys.lockKey("APP", "id1"));
    }

    @Test
    void channelIsNormalizedInKeys() {
        assertEquals("modality:captcha:data:MINI:id1", CaptchaKeys.dataKey("mini", "id1"));
    }

    @Test
    void blankChannelRejected() {
        assertThrows(IllegalArgumentException.class, () -> CaptchaKeys.dataKey(null, "id1"));
    }
}
