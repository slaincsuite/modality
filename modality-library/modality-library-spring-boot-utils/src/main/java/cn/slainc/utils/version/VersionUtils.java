package cn.slainc.utils.version;

import java.util.ArrayList;
import java.util.List;

public final class VersionUtils {

    private VersionUtils() {
    }

    public static int compare(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    public static boolean isGreaterThan(String left, String right) {
        return compare(left, right) > 0;
    }

    public static boolean isLessThan(String left, String right) {
        return compare(left, right) < 0;
    }

    public static boolean isEqual(String left, String right) {
        return compare(left, right) == 0;
    }

    public static boolean isAtLeast(String version, String minimum) {
        return compare(version, minimum) >= 0;
    }

    public static boolean isAtMost(String version, String maximum) {
        return compare(version, maximum) <= 0;
    }

    public static boolean isBetween(String version, String minimum, String maximum) {
        return isAtLeast(version, minimum) && isAtMost(version, maximum);
    }

    public static boolean satisfies(String version, String range) {
        if (version == null || range == null || range.isBlank()) {
            return false;
        }
        String expression = range.trim();
        if (expression.startsWith("^")) {
            String base = expression.substring(1).trim();
            int[] parsed = parse(base);
            String upper = parsed[0] + 1 + ".0.0";
            return isAtLeast(version, base) && isLessThan(version, upper);
        }
        if (expression.startsWith("~")) {
            String base = expression.substring(1).trim();
            int[] parsed = parse(base);
            String upper = parsed[0] + "." + (parsed[1] + 1) + ".0";
            return isAtLeast(version, base) && isLessThan(version, upper);
        }
        if (expression.startsWith(">=")) {
            return isAtLeast(version, expression.substring(2).trim());
        }
        if (expression.startsWith("<=")) {
            return isAtMost(version, expression.substring(2).trim());
        }
        if (expression.startsWith(">")) {
            return isGreaterThan(version, expression.substring(1).trim());
        }
        if (expression.startsWith("<")) {
            return isLessThan(version, expression.substring(1).trim());
        }
        return isEqual(version, expression);
    }

    public static String major(String version) {
        int[] parts = parse(version);
        return parts.length > 0 ? String.valueOf(parts[0]) : null;
    }

    public static String minor(String version) {
        int[] parts = parse(version);
        return parts.length > 1 ? String.valueOf(parts[1]) : null;
    }

    public static String patch(String version) {
        int[] parts = parse(version);
        return parts.length > 2 ? String.valueOf(parts[2]) : null;
    }

    public static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return new int[0];
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        int dash = normalized.indexOf('-');
        if (dash >= 0) {
            normalized = normalized.substring(0, dash);
        }
        int plus = normalized.indexOf('+');
        if (plus >= 0) {
            normalized = normalized.substring(0, plus);
        }
        List<Integer> parts = new ArrayList<>();
        for (String segment : normalized.split("\\.")) {
            try {
                parts.add(Integer.parseInt(segment.trim()));
            } catch (NumberFormatException ex) {
                parts.add(0);
            }
        }
        int[] result = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            result[i] = parts.get(i);
        }
        return result;
    }
}
