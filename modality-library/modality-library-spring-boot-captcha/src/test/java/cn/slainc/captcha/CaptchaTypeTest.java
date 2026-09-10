package cn.slainc.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaTypeTest {

    @Test
    void ofParsesIgnoringCase() {
        assertEquals(CaptchaType.SLIDE, CaptchaType.of("slide"));
        assertEquals(CaptchaType.CHAR_ALNUM, CaptchaType.of("CHAR_ALNUM"));
    }

    @Test
    void ofTrimsWhitespace() {
        assertEquals(CaptchaType.CLICK, CaptchaType.of("  CLICK "));
    }

    @Test
    void ofNullOrBlankReturnsNull() {
        assertNull(CaptchaType.of(null));
        assertNull(CaptchaType.of("  "));
    }

    @Test
    void ofUnknownReturnsNull() {
        assertNull(CaptchaType.of("未知类型"));
    }

    @Test
    void behaviorBasedFlagsSlideAndClick() {
        assertTrue(CaptchaType.SLIDE.behaviorBased());
        assertTrue(CaptchaType.CLICK.behaviorBased());
        assertFalse(CaptchaType.CHAR_ALNUM.behaviorBased());
        assertFalse(CaptchaType.ARITHMETIC.behaviorBased());
    }

    @Test
    void descriptionsAreChinese() {
        assertEquals("滑动拼图", CaptchaType.SLIDE.getDescription());
        assertEquals("文字点选", CaptchaType.CLICK.getDescription());
        assertEquals("数字字母混合", CaptchaType.CHAR_ALNUM.getDescription());
    }

    @Test
    void channelsNormalize() {
        assertEquals("WEB", Channels.normalize("web"));
        assertEquals("MINI", Channels.normalize(" Mini "));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> Channels.normalize(" "));
        assertTrue(exception.getMessage().contains("渠道"));
    }
}
