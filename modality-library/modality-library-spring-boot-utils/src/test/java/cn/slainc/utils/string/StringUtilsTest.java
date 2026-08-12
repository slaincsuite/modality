package cn.slainc.utils.string;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringUtilsTest {

    @Test
    void isEmptyAndBlank() {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertFalse(StringUtils.isEmpty(" "));
        assertTrue(StringUtils.isBlank("   "));
        assertTrue(StringUtils.isBlank(null));
        assertFalse(StringUtils.isBlank(" a "));
        assertTrue(StringUtils.isAnyBlank("a", " "));
        assertTrue(StringUtils.isAllBlank("", "  ", null));
        assertFalse(StringUtils.isAllBlank("", "a"));
    }

    @Test
    void trimHelpers() {
        assertEquals("a", StringUtils.trim(" a "));
        assertNull(StringUtils.trimToNull("   "));
        assertEquals("", StringUtils.trimToEmpty(null));
        assertEquals("a", StringUtils.trimToEmpty(" a "));
        assertEquals("d", StringUtils.defaultIfBlank("  ", "d"));
        assertNull(StringUtils.emptyToNull(""));
        assertEquals("", StringUtils.nullToEmpty(null));
    }

    @Test
    void caseAndPad() {
        assertEquals("Abc", StringUtils.capitalize("abc"));
        assertEquals("aBC", StringUtils.uncapitalize("ABC"));
        assertEquals("ABC", StringUtils.upperCase("abc"));
        assertEquals("007", StringUtils.leftPad("7", 3, '0'));
        assertEquals("700", StringUtils.rightPad("7", 3, '0'));
        assertEquals("abc", StringUtils.leftPad("abc", 2, '0'));
    }

    @Test
    void truncateAndAbbreviate() {
        assertEquals("abcde", StringUtils.truncate("abcdefg", 5));
        assertEquals("ab...", StringUtils.truncate("abcdefg", 5, "..."));
        assertEquals("ab...", StringUtils.abbreviate("abcdefg", 5));
    }

    @Test
    void mask() {
        assertEquals("138****5678", StringUtils.mask("13812345678", 3, 4));
        assertEquals("***", StringUtils.mask("abc", 2, 2));
        assertEquals("a******", StringUtils.mask("abcdefg", 1, 0));
        assertEquals("138****5678", StringUtils.maskMobile("13812345678"));
        assertTrue(StringUtils.maskEmail("abcdef@x.com").endsWith("@x.com"));
    }

    @Test
    void searchAndReplace() {
        assertEquals(3, StringUtils.countMatches("ababab", "ab"));
        assertEquals("b", StringUtils.substringBetween("<a>b</a>", "<a>", "</a>"));
        assertEquals("aa", StringUtils.substringBefore("aa@bb", "@"));
        assertEquals("bb", StringUtils.substringAfter("aa@bb", "@"));
        assertEquals("c", StringUtils.removePrefix("abc", "ab"));
        assertEquals("a", StringUtils.removeSuffix("abc", "bc"));
        assertTrue(StringUtils.startsWithIgnoreCase("Hello", "he"));
        assertTrue(StringUtils.endsWithIgnoreCase("Hello", "LO"));
        assertTrue(StringUtils.containsIgnoreCase("Hello", "ell"));
    }

    @Test
    void joinAndNumeric() {
        assertEquals("a,b,c", StringUtils.join(List.of("a", "b", "c"), ","));
        assertEquals("", StringUtils.join(List.of(), ","));
        assertTrue(StringUtils.isNumeric("12345"));
        assertFalse(StringUtils.isNumeric("12a"));
    }
}
