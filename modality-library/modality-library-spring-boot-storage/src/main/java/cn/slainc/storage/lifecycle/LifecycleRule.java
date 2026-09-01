package cn.slainc.storage.lifecycle;

import java.time.Duration;

public record LifecycleRule(String name, String prefix, Duration expireAfter, Duration archiveAfter,
                            String archiveBackend) {

    public static LifecycleRule expire(String name, String prefix, Duration expireAfter) {
        return new LifecycleRule(name, prefix, expireAfter, null, null);
    }

    public boolean matches(String key) {
        return prefix == null || prefix.isEmpty() || key.startsWith(prefix);
    }
}
