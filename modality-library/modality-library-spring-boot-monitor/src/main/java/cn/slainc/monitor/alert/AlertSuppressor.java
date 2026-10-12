package cn.slainc.monitor.alert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AlertSuppressor {

    private final Map<String, Long> lastNotified = new ConcurrentHashMap<>();

    public boolean suppress(String key, long nowMillis, long windowMillis) {
        if (key == null || key.isBlank() || windowMillis <= 0L) {
            return false;
        }
        AtomicBoolean suppressed = new AtomicBoolean(false);
        lastNotified.compute(key, (ruleId, last) -> {
            if (last != null && nowMillis - last < windowMillis) {
                suppressed.set(true);
                return last;
            }
            return nowMillis;
        });
        return suppressed.get();
    }

    public void release(String key, long suppressedAtMillis) {
        if (key != null) {
            lastNotified.remove(key, suppressedAtMillis);
        }
    }

    public void reset(String key) {
        if (key != null) {
            lastNotified.remove(key);
        }
    }

    public void clear() {
        lastNotified.clear();
    }
}
