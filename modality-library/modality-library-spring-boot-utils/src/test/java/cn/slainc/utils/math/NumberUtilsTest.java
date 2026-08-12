package cn.slainc.utils.math;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberUtilsTest {

    @Test
    void arithmetic() {
        assertEquals(new BigDecimal("3"), NumberUtils.add(BigDecimal.ONE, BigDecimal.valueOf(2)));
        assertEquals(new BigDecimal("-1"), NumberUtils.subtract(BigDecimal.ONE, BigDecimal.valueOf(2)));
        assertEquals(new BigDecimal("6"), NumberUtils.multiply(BigDecimal.valueOf(2), BigDecimal.valueOf(3)));
        assertEquals(new BigDecimal("3.33"), NumberUtils.divide(BigDecimal.TEN, BigDecimal.valueOf(3)));
        assertEquals(new BigDecimal("3.3333"),
                NumberUtils.divide(BigDecimal.TEN, BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP));
        assertEquals(BigDecimal.ZERO, NumberUtils.nullToZero(null));
        assertThrows(ArithmeticException.class, () -> NumberUtils.divide(BigDecimal.ONE, BigDecimal.ZERO));
    }

    @Test
    void percentAndRange() {
        assertEquals(new BigDecimal("25.00"),
                NumberUtils.percent(BigDecimal.ONE, BigDecimal.valueOf(4)));
        assertEquals(BigDecimal.ZERO.setScale(2), NumberUtils.percent(BigDecimal.ONE, BigDecimal.ZERO));
        assertTrue(NumberUtils.inRange(BigDecimal.valueOf(5), BigDecimal.ONE, BigDecimal.TEN));
        assertFalse(NumberUtils.inRange(BigDecimal.valueOf(11), BigDecimal.ONE, BigDecimal.TEN));
        assertFalse(NumberUtils.inRange(null, BigDecimal.ONE, BigDecimal.TEN));
    }

    @Test
    void formatting() {
        assertEquals("1,234.57", NumberUtils.formatThousands(new BigDecimal("1234.567")));
        assertEquals("12.35", NumberUtils.format(new BigDecimal("12.345"), 2));
        assertEquals(new BigDecimal("12.35"), NumberUtils.parse("12.35"));
        assertEquals(new BigDecimal("9"), NumberUtils.parse(null, new BigDecimal("9")));
        assertTrue(NumberUtils.isInteger("42"));
        assertFalse(NumberUtils.isInteger("4.2"));
    }

    @Test
    void sizeConversion() {
        assertEquals("0 B", NumberUtils.humanReadableSize(0));
        assertEquals("1 KB", NumberUtils.humanReadableSize(1024));
        assertEquals("1.5 KB", NumberUtils.humanReadableSize(1536));
        assertEquals("1 MB", NumberUtils.humanReadableSize(1024L * 1024L));
        assertEquals(1024L, NumberUtils.convertSize(1L, NumberUtils.SizeUnit.MB, NumberUtils.SizeUnit.KB));
        assertEquals(1048576L, NumberUtils.convertSize(1024L, NumberUtils.SizeUnit.KB, NumberUtils.SizeUnit.B));
    }
}
