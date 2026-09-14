package cn.slainc.session.cleanup;

import java.util.LinkedHashMap;
import java.util.Map;

public record CleanupReport(int removedTerminals, int idleKicked, int lifetimeKicked, boolean truncated,
                            String message) {

    public static CleanupReport empty() {
        return new CleanupReport(0, 0, 0, false, null);
    }

    public int total() {
        return removedTerminals + idleKicked + lifetimeKicked;
    }

    public CleanupReport merge(CleanupReport other) {
        if (other == null) {
            return this;
        }
        return new CleanupReport(removedTerminals + other.removedTerminals, idleKicked + other.idleKicked,
                lifetimeKicked + other.lifetimeKicked, truncated || other.truncated,
                message == null ? other.message : message);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("removedTerminals", removedTerminals);
        map.put("idleKicked", idleKicked);
        map.put("lifetimeKicked", lifetimeKicked);
        map.put("total", total());
        map.put("truncated", truncated);
        map.put("message", message);
        return map;
    }
}
