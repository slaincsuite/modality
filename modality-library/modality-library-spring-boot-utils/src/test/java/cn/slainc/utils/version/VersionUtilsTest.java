package cn.slainc.utils.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionUtilsTest {

    @Test
    void comparison() {
        assertTrue(VersionUtils.compare("1.2.3", "1.2.4") < 0);
        assertTrue(VersionUtils.compare("1.10.0", "1.9.0") > 0);
        assertEquals(0, VersionUtils.compare("1.2.3", "1.2.3"));
        assertEquals(0, VersionUtils.compare("v1.2.3", "1.2.3"));
        assertEquals(0, VersionUtils.compare("1.2.3-SNAPSHOT", "1.2.3"));
        assertTrue(VersionUtils.isGreaterThan("2.0.0", "1.9.9"));
        assertTrue(VersionUtils.isAtLeast("1.2.3", "1.2.3"));
        assertTrue(VersionUtils.isBetween("1.5.0", "1.0.0", "2.0.0"));
    }

    @Test
    void rangeMatching() {
        assertTrue(VersionUtils.satisfies("1.5.0", "^1.0.0"));
        assertFalse(VersionUtils.satisfies("2.0.0", "^1.0.0"));
        assertTrue(VersionUtils.satisfies("1.2.9", "~1.2.0"));
        assertFalse(VersionUtils.satisfies("1.3.0", "~1.2.0"));
        assertTrue(VersionUtils.satisfies("2.0.0", ">=1.0.0"));
        assertTrue(VersionUtils.satisfies("0.9.0", "<1.0.0"));
        assertTrue(VersionUtils.satisfies("1.0.0", "1.0.0"));
    }

    @Test
    void componentExtraction() {
        assertEquals("1", VersionUtils.major("1.2.3"));
        assertEquals("2", VersionUtils.minor("1.2.3"));
        assertEquals("3", VersionUtils.patch("1.2.3"));
        assertEquals(3, VersionUtils.parse("1.2.3").length);
    }
}
