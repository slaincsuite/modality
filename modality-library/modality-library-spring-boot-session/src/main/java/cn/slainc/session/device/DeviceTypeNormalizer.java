package cn.slainc.session.device;

import cn.slainc.web.ip.UserAgent;
import cn.slainc.web.ip.UserAgentParser;

import java.util.Locale;

public final class DeviceTypeNormalizer {

    public static final String PC = "PC";

    public static final String MOBILE = "MOBILE";

    public static final String TABLET = "TABLET";

    public static final String UNKNOWN = "UNKNOWN";

    private DeviceTypeNormalizer() {
    }

    public static String normalize(String deviceType) {
        return normalize(deviceType, null);
    }

    public static String normalize(String deviceType, String userAgent) {
        String raw = deviceType == null ? "" : deviceType.trim().toUpperCase(Locale.ROOT);
        String byName = byName(raw);
        if (byName != null) {
            return byName;
        }
        String byAgent = byAgent(userAgent);
        if (byAgent != null) {
            return byAgent;
        }
        String rawByAgent = byAgent(raw);
        return rawByAgent == null ? UNKNOWN : rawByAgent;
    }

    public static boolean matches(String deviceType, String userAgent, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String target = filter.trim();
        if (deviceType != null && deviceType.equalsIgnoreCase(target)) {
            return true;
        }
        String category = normalize(deviceType, userAgent);
        if (category.equalsIgnoreCase(target)) {
            return true;
        }
        return UNKNOWN.equalsIgnoreCase(target) && UNKNOWN.equals(category);
    }

    private static String byName(String raw) {
        if (raw.isEmpty()) {
            return null;
        }
        if (raw.contains("TABLET") || raw.contains("IPAD") || raw.contains("PAD")) {
            return TABLET;
        }
        if (raw.contains("MOBILE") || raw.contains("PHONE") || raw.contains("ANDROID") || raw.contains("IOS")
                || raw.contains("IPHONE") || raw.contains("WAP")) {
            return MOBILE;
        }
        if (raw.contains("PC") || raw.contains("WEB") || raw.contains("DESKTOP") || raw.contains("WINDOWS")
                || raw.contains("MAC") || raw.contains("LINUX") || raw.contains("CHROME")) {
            return PC;
        }
        return null;
    }

    private static String byAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        UserAgent parsed = UserAgentParser.parse(userAgent);
        if (parsed == null || parsed.device() == null) {
            return null;
        }
        return switch (parsed.device()) {
            case PC -> PC;
            case MOBILE -> MOBILE;
            case TABLET -> TABLET;
            default -> null;
        };
    }
}
