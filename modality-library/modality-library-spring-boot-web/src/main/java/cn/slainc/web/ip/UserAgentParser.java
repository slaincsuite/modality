package cn.slainc.web.ip;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UserAgentParser {

    private static final Map<String, Pattern> BOT_PATTERNS = new LinkedHashMap<>();
    private static final Map<String, Pattern> BROWSER_PATTERNS = new LinkedHashMap<>();
    private static final Map<String, Pattern> OS_PATTERNS = new LinkedHashMap<>();

    static {
        BOT_PATTERNS.put("Googlebot", Pattern.compile("Googlebot", Pattern.CASE_INSENSITIVE));
        BOT_PATTERNS.put("Bingbot", Pattern.compile("bingbot", Pattern.CASE_INSENSITIVE));
        BOT_PATTERNS.put("Baiduspider", Pattern.compile("Baiduspider", Pattern.CASE_INSENSITIVE));
        BOT_PATTERNS.put("YandexBot", Pattern.compile("YandexBot", Pattern.CASE_INSENSITIVE));
        BOT_PATTERNS.put("DuckDuckBot", Pattern.compile("DuckDuckBot", Pattern.CASE_INSENSITIVE));
        BOT_PATTERNS.put("Sogou", Pattern.compile("Sogou", Pattern.CASE_INSENSITIVE));
        BOT_PATTERNS.put("360Spider", Pattern.compile("360Spider", Pattern.CASE_INSENSITIVE));
        BOT_PATTERNS.put("AhrefsBot", Pattern.compile("AhrefsBot", Pattern.CASE_INSENSITIVE));
        BOT_PATTERNS.put("SemrushBot", Pattern.compile("SemrushBot", Pattern.CASE_INSENSITIVE));
        BOT_PATTERNS.put("curl", Pattern.compile("^curl/", Pattern.CASE_INSENSITIVE));
        BOT_PATTERNS.put("Wget", Pattern.compile("^Wget/", Pattern.CASE_INSENSITIVE));

        BROWSER_PATTERNS.put("Edge", Pattern.compile("Edg(?:e|A|iOS)?/([\\d.]+)"));
        BROWSER_PATTERNS.put("Opera", Pattern.compile("(?:OPR|Opera)/([\\d.]+)"));
        BROWSER_PATTERNS.put("Chrome", Pattern.compile("Chrome/([\\d.]+)"));
        BROWSER_PATTERNS.put("Firefox", Pattern.compile("Firefox/([\\d.]+)"));
        BROWSER_PATTERNS.put("Safari", Pattern.compile("Version/([\\d.]+).*Safari"));
        BROWSER_PATTERNS.put("IE", Pattern.compile("MSIE ([\\d.]+)"));

        OS_PATTERNS.put("Android", Pattern.compile("Android[ /]([\\d.]+)"));
        OS_PATTERNS.put("iOS", Pattern.compile("(?:iPhone|iPad|iPod).*OS ([\\d_]+)"));
        OS_PATTERNS.put("Windows", Pattern.compile("Windows NT ([\\d.]+)"));
        OS_PATTERNS.put("macOS", Pattern.compile("Mac OS X ([\\d_.]+)"));
        OS_PATTERNS.put("Linux", Pattern.compile("Linux"));
        OS_PATTERNS.put("HarmonyOS", Pattern.compile("HarmonyOS", Pattern.CASE_INSENSITIVE));
    }

    private UserAgentParser() {
    }

    public static UserAgent parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UserAgent.UNKNOWN;
        }
        String text = userAgent.trim();
        String bot = detectBot(text);
        if (bot != null) {
            return new UserAgent(bot, null, detectOs(text), UserAgent.DeviceType.BOT, true, text);
        }
        String browser = "未知";
        String version = null;
        for (Map.Entry<String, Pattern> entry : BROWSER_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(text);
            if (matcher.find()) {
                browser = entry.getKey();
                version = matcher.groupCount() >= 1 ? matcher.group(1) : null;
                break;
            }
        }
        return new UserAgent(browser, version, detectOs(text), detectDevice(text), false, text);
    }

    private static String detectBot(String text) {
        for (Map.Entry<String, Pattern> entry : BOT_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(text).find()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String detectOs(String text) {
        for (Map.Entry<String, Pattern> entry : OS_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(text).find()) {
                return entry.getKey();
            }
        }
        return "未知";
    }

    private static UserAgent.DeviceType detectDevice(String text) {
        if (text.matches("(?i).*(iPad|Tablet|Nexus 7|Nexus 10|SM-T).*")) {
            return UserAgent.DeviceType.TABLET;
        }
        if (text.matches("(?i).*(Mobile|iPhone|Android.*Mobile|Windows Phone).*")) {
            return UserAgent.DeviceType.MOBILE;
        }
        if (text.matches("(?i).*(Windows|Macintosh|Linux|X11|CrOS).*")) {
            return UserAgent.DeviceType.PC;
        }
        return UserAgent.DeviceType.UNKNOWN;
    }
}
