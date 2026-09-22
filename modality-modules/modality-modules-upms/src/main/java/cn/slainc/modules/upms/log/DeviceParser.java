package cn.slainc.modules.upms.log;

public final class DeviceParser {

    private DeviceParser() {
    }

    public static String parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "tablet";
        }
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "mobile";
        }
        if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("linux")) {
            return "pc";
        }
        return "unknown";
    }
}
