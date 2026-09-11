package cn.slainc.session;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record SessionInfo(String loginType, String loginId, String tokenValue, String deviceType,
                          String deviceCategory, String deviceId, String clientIp, String userAgent,
                          LocalDateTime loginTime, LocalDateTime lastActiveTime, long timeoutSeconds,
                          long remainingSeconds, boolean active, Map<String, Object> extended) {

    public SessionInfo {
        extended = extended == null ? Map.of() : new LinkedHashMap<>(extended);
    }

    public Long tenantId() {
        Object raw = extended.get("tenantId");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String tenantKey() {
        Long tenantId = tenantId();
        return tenantId == null ? null : String.valueOf(tenantId);
    }
}
