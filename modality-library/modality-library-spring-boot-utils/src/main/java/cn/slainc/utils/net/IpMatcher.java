package cn.slainc.utils.net;

import inet.ipaddr.IPAddressString;

public final class IpMatcher {

    private IpMatcher() {
    }

    public static boolean matches(String requestIp, String entry) {
        if (requestIp == null || entry == null || entry.isBlank()) {
            return false;
        }
        String candidate = entry.trim();
        if (candidate.equals(requestIp)) {
            return true;
        }
        try {
            return new IPAddressString(candidate).contains(new IPAddressString(requestIp));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static boolean matchesAny(String requestIp, Iterable<String> entries) {
        if (entries == null) {
            return false;
        }
        for (String entry : entries) {
            if (matches(requestIp, entry)) {
                return true;
            }
        }
        return false;
    }
}
