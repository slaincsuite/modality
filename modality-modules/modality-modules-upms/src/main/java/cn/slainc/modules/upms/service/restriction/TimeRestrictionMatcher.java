package cn.slainc.modules.upms.service.restriction;

import java.time.LocalTime;
import java.time.ZoneId;

public final class TimeRestrictionMatcher {

    private TimeRestrictionMatcher() {
    }

    // 规则：逗号分隔的每日时段 HH:mm-HH:mm，支持跨天窗口（如 22:00-06:00），空值表示不限制
    public static boolean matches(String allowedTimeRange, long epochMillis) {
        if (allowedTimeRange == null || allowedTimeRange.isBlank()) {
            return true;
        }
        LocalTime now = java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime();
        for (String range : allowedTimeRange.split(",")) {
            String[] parts = range.trim().split("-");
            if (parts.length != 2) {
                continue;
            }
            try {
                LocalTime start = LocalTime.parse(parts[0].trim());
                LocalTime end = LocalTime.parse(parts[1].trim());
                if (start.isAfter(end) || start.equals(end)) {
                    if (!now.isBefore(start) || now.isBefore(end)) {
                        return true;
                    }
                } else if (!now.isBefore(start) && now.isBefore(end)) {
                    return true;
                }
            } catch (java.time.format.DateTimeParseException ignored) {
                // 非法时段规则按不生效处理，避免把账号彻底锁死
            }
        }
        return false;
    }
}
