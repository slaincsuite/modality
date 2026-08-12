package cn.slainc.utils.math;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public final class NumberUtils {

    public static final int DEFAULT_SCALE = 2;

    public enum SizeUnit {
        B(1L),
        KB(1024L),
        MB(1024L * 1024L),
        GB(1024L * 1024L * 1024L),
        TB(1024L * 1024L * 1024L * 1024L);

        private final long factor;

        SizeUnit(long factor) {
            this.factor = factor;
        }

        public long factor() {
            return factor;
        }
    }

    private NumberUtils() {
    }

    public static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal add(BigDecimal left, BigDecimal right) {
        return nullToZero(left).add(nullToZero(right));
    }

    public static BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return nullToZero(left).subtract(nullToZero(right));
    }

    public static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return nullToZero(left).multiply(nullToZero(right));
    }

    public static BigDecimal divide(BigDecimal left, BigDecimal right) {
        return divide(left, right, DEFAULT_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal divide(BigDecimal left, BigDecimal right, int scale, RoundingMode roundingMode) {
        BigDecimal divisor = nullToZero(right);
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("除数不能为零");
        }
        return nullToZero(left).divide(divisor, scale, roundingMode);
    }

    public static BigDecimal scale(BigDecimal value, int scale, RoundingMode roundingMode) {
        return nullToZero(value).setScale(scale, roundingMode);
    }

    public static BigDecimal scale(BigDecimal value, int scale) {
        return scale(value, scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(BigDecimal part, BigDecimal total) {
        return percent(part, total, DEFAULT_SCALE);
    }

    public static BigDecimal percent(BigDecimal part, BigDecimal total, int scale) {
        BigDecimal base = nullToZero(total);
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return nullToZero(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(base, scale, RoundingMode.HALF_UP);
    }

    public static String format(BigDecimal value, int scale) {
        return scale(value, scale).toPlainString();
    }

    public static String formatThousands(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return new DecimalFormat("#,##0.00").format(value);
    }

    public static String formatThousands(BigDecimal value, int scale) {
        if (value == null) {
            return null;
        }
        DecimalFormat format = new DecimalFormat("#,##0");
        format.setMinimumFractionDigits(scale);
        format.setMaximumFractionDigits(scale);
        return format.format(value);
    }

    public static String formatCurrency(BigDecimal value) {
        return formatCurrency(value, Locale.CHINA);
    }

    public static String formatCurrency(BigDecimal value, Locale locale) {
        if (value == null) {
            return null;
        }
        return NumberFormat.getCurrencyInstance(locale).format(value);
    }

    public static boolean inRange(BigDecimal value, BigDecimal minInclusive, BigDecimal maxInclusive) {
        if (value == null) {
            return false;
        }
        if (minInclusive != null && value.compareTo(minInclusive) < 0) {
            return false;
        }
        return maxInclusive == null || value.compareTo(maxInclusive) <= 0;
    }

    public static long convertSize(long value, SizeUnit from, SizeUnit to) {
        if (from == null || to == null || from == to) {
            return value;
        }
        BigDecimal bytes = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(from.factor()));
        return bytes.divide(BigDecimal.valueOf(to.factor()), 0, RoundingMode.HALF_UP).longValue();
    }

    public static String humanReadableSize(long bytes) {
        if (bytes < 0) {
            return "-" + humanReadableSize(-bytes);
        }
        if (bytes < SizeUnit.KB.factor()) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB"};
        double value = bytes;
        int unitIndex = -1;
        do {
            value /= 1024d;
            unitIndex++;
        } while (value >= 1024d && unitIndex < units.length - 1);
        return new DecimalFormat("#,##0.##").format(value) + " " + units[unitIndex];
    }

    public static boolean isInteger(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(text.trim());
            return text.trim().indexOf('.') < 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public static BigDecimal parse(String text) {
        return text == null || text.isBlank() ? null : new BigDecimal(text.trim());
    }

    public static BigDecimal parse(String text, BigDecimal defaultValue) {
        BigDecimal value = parse(text);
        return value == null ? defaultValue : value;
    }
}
