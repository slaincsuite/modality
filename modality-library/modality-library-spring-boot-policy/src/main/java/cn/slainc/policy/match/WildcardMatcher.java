package cn.slainc.policy.match;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class WildcardMatcher {

    private static final int PATTERN_CACHE_LIMIT = 2048;

    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    private WildcardMatcher() {
    }

    public static boolean match(String pattern, String value) {
        if (pattern == null || value == null) {
            return false;
        }
        if (pattern.equals("*")) {
            return true;
        }
        if (!pattern.contains("*")) {
            return pattern.equals(value);
        }
        return compiled(pattern).matcher(value).matches();
    }

    public static boolean matchAny(List<String> patterns, String value) {
        if (patterns == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (match(pattern, value)) {
                return true;
            }
        }
        return false;
    }

    public static String firstMatch(List<String> patterns, String value) {
        if (patterns == null) {
            return null;
        }
        for (String pattern : patterns) {
            if (match(pattern, value)) {
                return pattern;
            }
        }
        return null;
    }

    private static Pattern compiled(String pattern) {
        Pattern compiled = PATTERN_CACHE.get(pattern);
        if (compiled != null) {
            return compiled;
        }
        if (PATTERN_CACHE.size() >= PATTERN_CACHE_LIMIT) {
            PATTERN_CACHE.clear();
        }
        StringBuilder regex = new StringBuilder(pattern.length() + 16);
        for (char current : pattern.toCharArray()) {
            if (current == '*') {
                regex.append(".*");
            } else if (isRegexMeta(current)) {
                regex.append('\\').append(current);
            } else {
                regex.append(current);
            }
        }
        compiled = Pattern.compile(regex.toString());
        PATTERN_CACHE.put(pattern, compiled);
        return compiled;
    }

    private static boolean isRegexMeta(char current) {
        return ".^$+{[]|()?\\".indexOf(current) >= 0;
    }
}
