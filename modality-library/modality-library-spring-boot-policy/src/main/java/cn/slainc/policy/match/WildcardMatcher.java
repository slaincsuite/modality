package cn.slainc.policy.match;

import java.util.List;

public final class WildcardMatcher {

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
        // 线性通配匹配：多 * 模式下不会像正则回溯那样指数级退化
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int starValueIndex = 0;
        int patternLength = pattern.length();
        int valueLength = value.length();
        while (valueIndex < valueLength) {
            if (patternIndex < patternLength && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                starValueIndex = valueIndex;
            } else if (patternIndex < patternLength && pattern.charAt(patternIndex) == value.charAt(valueIndex)) {
                patternIndex++;
                valueIndex++;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++starValueIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < patternLength && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == patternLength;
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
}
