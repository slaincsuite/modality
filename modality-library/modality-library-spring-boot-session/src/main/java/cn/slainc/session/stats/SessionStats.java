package cn.slainc.session.stats;

import java.util.LinkedHashMap;
import java.util.Map;

public record SessionStats(long total, Map<String, Long> byType, Map<String, Long> byDeviceType,
                           Map<String, Long> byTenant, double avgDurationSeconds, boolean truncated,
                           String message) {

    public SessionStats {
        byType = byType == null ? new LinkedHashMap<>() : new LinkedHashMap<>(byType);
        byDeviceType = byDeviceType == null ? new LinkedHashMap<>() : new LinkedHashMap<>(byDeviceType);
        byTenant = byTenant == null ? new LinkedHashMap<>() : new LinkedHashMap<>(byTenant);
    }
}
