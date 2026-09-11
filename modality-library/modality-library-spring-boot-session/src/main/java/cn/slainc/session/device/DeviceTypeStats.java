package cn.slainc.session.device;

import java.util.LinkedHashMap;
import java.util.Map;

public record DeviceTypeStats(Map<String, Long> counts, long total, boolean truncated, String message) {

    public DeviceTypeStats {
        counts = counts == null ? new LinkedHashMap<>() : new LinkedHashMap<>(counts);
    }
}
