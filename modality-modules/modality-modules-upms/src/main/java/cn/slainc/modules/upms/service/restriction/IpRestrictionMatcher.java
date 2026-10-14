package cn.slainc.modules.upms.service.restriction;

public final class IpRestrictionMatcher {

    private IpRestrictionMatcher() {
    }

    // 规则：逗号分隔的精确 IP 或前缀通配（如 192.168.*），空值表示不限制
    public static boolean matches(String allowedIps, String clientIp) {
        if (allowedIps == null || allowedIps.isBlank()) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        for (String rule : allowedIps.split(",")) {
            String pattern = rule.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            if (pattern.endsWith("*")) {
                if (clientIp.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
            } else if (pattern.equalsIgnoreCase(clientIp)) {
                return true;
            }
        }
        return false;
    }
}
