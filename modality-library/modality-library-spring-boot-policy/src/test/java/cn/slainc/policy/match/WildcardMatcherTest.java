package cn.slainc.policy.match;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildcardMatcherTest {

    @Test
    void fullWildcardMatchesAnything() {
        assertTrue(WildcardMatcher.match("*", "任意内容"));
        assertTrue(WildcardMatcher.match("*", "modality:user:delete"));
    }

    @Test
    void exactMatchWithoutWildcard() {
        assertTrue(WildcardMatcher.match("modality:user:get", "modality:user:get"));
        assertFalse(WildcardMatcher.match("modality:user:get", "modality:user:list"));
    }

    @Test
    void prefixWildcardCrossesSegments() {
        assertTrue(WildcardMatcher.match("modality:user:*", "modality:user:get"));
        assertTrue(WildcardMatcher.match("modality:user:*", "modality:user:sub:detail:get"));
    }

    @Test
    void suffixAndMiddleWildcard() {
        assertTrue(WildcardMatcher.match("*:delete", "modality:user:delete"));
        assertFalse(WildcardMatcher.match("*:delete", "modality:user:delete:extra"));
        assertTrue(WildcardMatcher.match("modality:*:get", "modality:user:get"));
        assertTrue(WildcardMatcher.match("modality:*:get", "modality:order:get"));
        assertFalse(WildcardMatcher.match("modality:*:get", "modality:user:list"));
    }

    @Test
    void multipleWildcardsCombined() {
        assertTrue(WildcardMatcher.match("*user*get*", "modality:user:detail:get:1"));
        assertFalse(WildcardMatcher.match("*user*get*", "modality:order:get"));
    }

    @Test
    void pathStyleResourceMatch() {
        assertTrue(WildcardMatcher.match("modality:user/*", "modality:user/1001"));
        assertTrue(WildcardMatcher.match("modality:user/*", "modality:user/1001/profile"));
        assertFalse(WildcardMatcher.match("modality:user/*", "modality:order/1001"));
    }

    @Test
    void regexMetaCharsTreatedLiterally() {
        assertTrue(WildcardMatcher.match("modality:user:get(1)", "modality:user:get(1)"));
        assertFalse(WildcardMatcher.match("modality:user:get(1)", "modality:user:getX1Y"));
        assertTrue(WildcardMatcher.match("a.b*c", "a.bxyzc"));
        assertFalse(WildcardMatcher.match("a.b*c", "axbyc"));
    }

    @Test
    void manyWildcardsDoNotBacktrackExponentially() {
        String pattern = "*a*a*a*a*a*a*a*a*a*a*b";
        String value = "a".repeat(400);
        long start = System.nanoTime();
        assertFalse(WildcardMatcher.match(pattern, value));
        long millis = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(millis < 1_000L, "多 * 模式必须线性匹配，实际耗时 " + millis + " 毫秒");
    }

    @Test
    void nullValuesNeverMatch() {
        assertFalse(WildcardMatcher.match(null, "x"));
        assertFalse(WildcardMatcher.match("x", null));
        assertFalse(WildcardMatcher.match("*", null));
    }

    @Test
    void matchAnyAndFirstMatchFollowListSemantics() {
        assertTrue(WildcardMatcher.matchAny(List.of("a:b:c", "a:b:*"), "a:b:d"));
        assertFalse(WildcardMatcher.matchAny(List.of(), "a:b:d"));
        assertNull(WildcardMatcher.firstMatch(List.of("a:b:c"), "a:b:d"));
        org.junit.jupiter.api.Assertions.assertEquals("a:b:*",
                WildcardMatcher.firstMatch(List.of("a:b:*", "a:*"), "a:b:d"));
    }
}
