package cn.slainc.core.support;

import java.util.Collection;

public final class PermissionMatcher {

    public static final String DENY_PREFIX = "!";

    private PermissionMatcher() {
    }

    public static boolean matches(String grantedPattern, String requiredPermission) {
        if (grantedPattern == null || requiredPermission == null) {
            return false;
        }
        if ("*".equals(grantedPattern) || grantedPattern.equals(requiredPermission)) {
            return true;
        }
        if (!grantedPattern.contains("*")) {
            return false;
        }
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int starValueIndex = 0;
        while (valueIndex < requiredPermission.length()) {
            if (patternIndex < grantedPattern.length() && grantedPattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                starValueIndex = valueIndex;
            } else if (patternIndex < grantedPattern.length()
                    && grantedPattern.charAt(patternIndex) == requiredPermission.charAt(valueIndex)) {
                patternIndex++;
                valueIndex++;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++starValueIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < grantedPattern.length() && grantedPattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == grantedPattern.length();
    }

    public static boolean matchesAny(Collection<String> grantedPatterns, String requiredPermission) {
        if (grantedPatterns == null) {
            return false;
        }
        for (String pattern : grantedPatterns) {
            if (matches(pattern, requiredPermission)) {
                return true;
            }
        }
        return false;
    }

    public static boolean granted(Collection<String> grantedPatterns, String requiredPermission) {
        if (grantedPatterns == null || requiredPermission == null) {
            return false;
        }
        if (denied(grantedPatterns, requiredPermission)) {
            return false;
        }
        for (String pattern : grantedPatterns) {
            if (pattern != null && !pattern.startsWith(DENY_PREFIX)
                    && matches(pattern, requiredPermission)) {
                return true;
            }
        }
        return false;
    }

    public static boolean denied(Collection<String> grantedPatterns, String requiredPermission) {
        if (grantedPatterns == null || requiredPermission == null) {
            return false;
        }
        for (String pattern : grantedPatterns) {
            if (pattern != null && pattern.startsWith(DENY_PREFIX)
                    && matches(pattern.substring(DENY_PREFIX.length()), requiredPermission)) {
                return true;
            }
        }
        return false;
    }
}
