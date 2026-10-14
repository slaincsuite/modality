package cn.slainc.webhook.support;

import cn.slainc.utils.string.StringUtils;

import java.util.Locale;

public final class WebhookEventMatcher {

    private WebhookEventMatcher() {
    }

    public static boolean matches(String subscribedEvents, String eventType) {
        if (StringUtils.isBlank(subscribedEvents) || StringUtils.isBlank(eventType)) {
            return false;
        }
        String target = eventType.trim().toLowerCase(Locale.ROOT);
        for (String pattern : splitPatterns(subscribedEvents)) {
            if (WebhookEventTypes.WILDCARD.equals(pattern)) {
                return true;
            }
            if (pattern.equalsIgnoreCase(target)) {
                return true;
            }
            if (pattern.endsWith(WebhookEventTypes.WILDCARD)) {
                String prefix = pattern.substring(0, pattern.length() - WebhookEventTypes.WILDCARD.length())
                        .toLowerCase(Locale.ROOT);
                if (target.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String[] splitPatterns(String subscribedEvents) {
        String[] rawPatterns = subscribedEvents.split(",");
        String[] patterns = new String[rawPatterns.length];
        int count = 0;
        for (String raw : rawPatterns) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                patterns[count++] = trimmed;
            }
        }
        String[] compact = new String[count];
        System.arraycopy(patterns, 0, compact, 0, count);
        return compact;
    }
}
