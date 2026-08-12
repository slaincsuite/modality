package cn.slainc.utils.string;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexUtilsTest {

    @Test
    void predefinedPatterns() {
        assertTrue(RegexUtils.isMobile("13812345678"));
        assertFalse(RegexUtils.isMobile("12812345678"));
        assertTrue(RegexUtils.isEmail("user@example.com"));
        assertFalse(RegexUtils.isEmail("user@@example.com"));
        assertTrue(RegexUtils.isIpv4("192.168.1.1"));
        assertFalse(RegexUtils.isIpv4("256.1.1.1"));
        assertTrue(RegexUtils.isUrl("https://example.com/path"));
        assertFalse(RegexUtils.isUrl("not-a-url"));
        assertTrue(RegexUtils.isChinese("中文"));
        assertFalse(RegexUtils.isChinese("中文abc"));
        assertTrue(RegexUtils.isBankCard("6222021234567890123"));
        assertTrue(RegexUtils.isStrongPassword("Abc12345!"));
        assertFalse(RegexUtils.isStrongPassword("abc12345"));
    }

    @Test
    void extraction() {
        assertEquals("34", RegexUtils.extract("(\\d+)-(\\d+)", "12-34", 2));
        assertEquals(List.of("12", "56"), RegexUtils.extractAll("\\d+(?=,|$)", "12,34".replace("34", "56")));
        assertTrue(RegexUtils.hasMatch("\\d+", "abc123"));
        assertEquals("x", RegexUtils.replaceAll("\\d+", "a1b", "x").substring(1, 2));
    }
}
