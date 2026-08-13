package cn.slainc.utils.lang;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssertTest {

    @Test
    void trueAndFalse() {
        Assert.isTrue(true, "必须为真");
        Assert.isFalse(false, "必须为假");
        assertThrows(IllegalArgumentException.class, () -> Assert.isTrue(false, "模拟异常"));
        assertThrows(IllegalStateException.class,
                () -> Assert.isTrue(false, () -> new IllegalStateException("自定义异常")));
    }

    @Test
    void notNullAndHasText() {
        assertEquals("v", Assert.notNull("v", "不能为空"));
        assertThrows(IllegalArgumentException.class, () -> Assert.notNull(null, "不能为空"));
        assertThrows(IllegalStateException.class,
                () -> Assert.notNull(null, () -> new IllegalStateException("自定义异常")));
        assertEquals(" text ", Assert.hasText(" text ", "不能为空"));
        assertThrows(IllegalArgumentException.class, () -> Assert.hasText("  ", "不能为空"));
        assertThrows(IllegalArgumentException.class, () -> Assert.hasText(null, "不能为空"));
    }

    @Test
    void collectionsAndMaps() {
        assertEquals(List.of(1), Assert.notEmpty(List.of(1), "不能为空"));
        assertThrows(IllegalArgumentException.class, () -> Assert.notEmpty(List.of(), "不能为空"));
        assertEquals(Map.of("a", 1), Assert.notEmpty(Map.of("a", 1), "不能为空"));
        assertThrows(IllegalArgumentException.class, () -> Assert.notEmpty(Map.of(), "不能为空"));
        assertEquals(1, Assert.notEmpty(new Integer[]{1}, "不能为空").length);
        assertThrows(IllegalArgumentException.class, () -> Assert.notEmpty(new Integer[0], "不能为空"));
    }

    @Test
    void rangeAndState() {
        assertEquals(5L, Assert.inRange(5L, 1L, 10L, "超出范围"));
        assertThrows(IllegalArgumentException.class, () -> Assert.inRange(11L, 1L, 10L, "超出范围"));
        assertEquals(5, Assert.inRange(5, 1, 10, "超出范围"));
        assertThrows(IllegalArgumentException.class, () -> Assert.inRange(0, 1, 10, "超出范围"));
        Assert.isPositive(1L, "必须为正数");
        assertThrows(IllegalArgumentException.class, () -> Assert.isPositive(0L, "必须为正数"));
        Assert.notNegative(0L, "不能为负数");
        assertThrows(IllegalArgumentException.class, () -> Assert.notNegative(-1L, "不能为负数"));
        Assert.state(true, "状态不合法");
        assertThrows(IllegalStateException.class, () -> Assert.state(false, "状态不合法"));
    }
}
