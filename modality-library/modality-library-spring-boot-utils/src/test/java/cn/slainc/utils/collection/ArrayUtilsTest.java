package cn.slainc.utils.collection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrayUtilsTest {

    @Test
    void emptinessChecks() {
        assertTrue(ArrayUtils.isEmpty((Object[]) null));
        assertTrue(ArrayUtils.isEmpty(new Object[0]));
        assertTrue(ArrayUtils.isEmpty(new byte[0]));
        assertTrue(ArrayUtils.isEmpty(new int[0]));
        assertTrue(ArrayUtils.isEmpty(new long[0]));
        assertFalse(ArrayUtils.isEmpty(new byte[]{1}));
        assertTrue(ArrayUtils.isNotEmpty(new byte[]{1}));
        assertTrue(ArrayUtils.isNotEmpty(new Object[]{"a"}));
    }

    @Test
    void conversions() {
        assertEquals(List.of("a", "b"), ArrayUtils.toList(new String[]{"a", "b"}));
        assertEquals(List.of(1, 2), ArrayUtils.toList(new int[]{1, 2}));
        assertEquals(List.of(1L, 2L), ArrayUtils.toList(new long[]{1L, 2L}));
        assertArrayEquals(new String[]{"a", "b"}, ArrayUtils.toArray(List.of("a", "b"), String.class));
        assertEquals(0, ArrayUtils.toArray(null, String.class).length);
    }

    @Test
    void slicingAndConcatenation() {
        assertArrayEquals(new String[]{"b", "c"}, ArrayUtils.subArray(new String[]{"a", "b", "c", "d"}, 1, 3));
        assertArrayEquals(new String[]{"a"}, ArrayUtils.subArray(new String[]{"a"}, 0, 99));
        assertArrayEquals(new byte[]{2, 3}, ArrayUtils.subArray(new byte[]{1, 2, 3}, 1, 3));
        assertArrayEquals(new String[]{"a", "b"}, ArrayUtils.concat(new String[]{"a"}, new String[]{"b"}, String.class));
        assertArrayEquals(new byte[]{1, 2}, ArrayUtils.concat(new byte[]{1}, new byte[]{2}));
    }

    @Test
    void accessors() {
        assertTrue(ArrayUtils.contains(new String[]{"a", "b"}, "b"));
        assertFalse(ArrayUtils.contains(new String[]{"a"}, "z"));
        assertFalse(ArrayUtils.contains(null, "z"));
        assertEquals("a", ArrayUtils.first(new String[]{"a", "b"}));
        assertEquals("b", ArrayUtils.last(new String[]{"a", "b"}));
        assertNull(ArrayUtils.first(new String[0]));
        assertNull(ArrayUtils.last(null));
    }
}
