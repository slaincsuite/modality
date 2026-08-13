package cn.slainc.utils.net;

import java.util.ArrayList;
import java.util.List;

public final class IpUtils {

    public static final String UNKNOWN = "unknown";
    public static final String LOCALHOST_IPV4 = "127.0.0.1";
    public static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    private IpUtils() {
    }

    public static boolean isIpv4(String ip) {
        return ip != null && cn.slainc.utils.string.RegexUtils.isIpv4(ip.trim());
    }

    public static boolean isIpv6(String ip) {
        return ip != null && cn.slainc.utils.string.RegexUtils.isIpv6(ip.trim());
    }

    public static boolean isIp(String ip) {
        return isIpv4(ip) || isIpv6(ip);
    }

    public static boolean isLocalhost(String ip) {
        if (ip == null) {
            return false;
        }
        String trimmed = ip.trim();
        return LOCALHOST_IPV4.equals(trimmed) || LOCALHOST_IPV6.equals(trimmed);
    }

    public static boolean isPrivateIp(String ip) {
        if (!isIpv4(ip)) {
            return false;
        }
        long value = ipToLong(ip.trim());
        return inCidr(value, "10.0.0.0", 8)
                || inCidr(value, "172.16.0.0", 12)
                || inCidr(value, "192.168.0.0", 16)
                || inCidr(value, "127.0.0.0", 8)
                || inCidr(value, "169.254.0.0", 16);
    }

    public static boolean isPublicIp(String ip) {
        return isIpv4(ip) && !isPrivateIp(ip);
    }

    public static long ipToLong(String ip) {
        if (!isIpv4(ip)) {
            throw new IllegalArgumentException("非法的 IPv4 地址: " + ip);
        }
        String[] segments = ip.trim().split("\\.");
        long value = 0L;
        for (String segment : segments) {
            value = (value << 8) | Integer.parseInt(segment);
        }
        return value;
    }

    public static String longToIp(long value) {
        return ((value >> 24) & 0xFF) + "." + ((value >> 16) & 0xFF) + "." + ((value >> 8) & 0xFF) + "." + (value & 0xFF);
    }

    public static boolean isInRange(String ip, String cidr) {
        if (!isIpv4(ip) || cidr == null || !cidr.contains("/")) {
            return false;
        }
        String[] parts = cidr.split("/");
        int prefix = Integer.parseInt(parts[1].trim());
        return inCidr(ipToLong(ip.trim()), parts[0].trim(), prefix);
    }

    private static boolean inCidr(long value, String network, int prefix) {
        long mask = prefix == 0 ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (value & mask) == (ipToLong(network) & mask);
    }

    public static List<String> splitProxyChain(String header) {
        List<String> result = new ArrayList<>();
        if (header == null || header.isBlank()) {
            return result;
        }
        for (String part : header.split(",")) {
            String candidate = part.trim();
            if (!candidate.isEmpty() && !UNKNOWN.equalsIgnoreCase(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    public static String resolveClientIp(String xForwardedFor, String xRealIp, String cfConnectingIp, String remoteAddr) {
        for (String candidate : new String[]{cfConnectingIp, xRealIp}) {
            if (candidate != null && !candidate.isBlank() && !UNKNOWN.equalsIgnoreCase(candidate.trim())) {
                return candidate.trim();
            }
        }
        List<String> chain = splitProxyChain(xForwardedFor);
        if (!chain.isEmpty()) {
            return chain.get(0);
        }
        return remoteAddr;
    }
}
