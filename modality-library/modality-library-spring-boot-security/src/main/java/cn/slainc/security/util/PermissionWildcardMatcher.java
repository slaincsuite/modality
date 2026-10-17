package cn.slainc.security.util;

import cn.slainc.core.support.PermissionMatcher;

import java.util.Collection;

public final class PermissionWildcardMatcher {

    private PermissionWildcardMatcher() {
    }

    public static boolean matches(String grantedPattern, String requiredPermission) {
        return PermissionMatcher.matches(grantedPattern, requiredPermission);
    }

    public static boolean matchesAny(Collection<String> grantedPatterns, String requiredPermission) {
        return PermissionMatcher.matchesAny(grantedPatterns, requiredPermission);
    }

    public static boolean granted(Collection<String> grantedPatterns, String requiredPermission) {
        return PermissionMatcher.granted(grantedPatterns, requiredPermission);
    }
}
