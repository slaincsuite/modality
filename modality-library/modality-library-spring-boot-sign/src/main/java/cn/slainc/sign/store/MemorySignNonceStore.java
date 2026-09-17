package cn.slainc.sign.store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MemorySignNonceStore implements SignNonceStore {

    private static final long DEFAULT_TTL_SECONDS = 300;

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String appId, String nonce, long ttlSeconds) {
        String key = "nonce:" + appId + ":" + nonce;
        Entry existing = liveEntry(key);
        if (existing != null) {
            return false;
        }
        Entry created = new Entry(expireAt(ttlSeconds));
        return entries.putIfAbsent(key, created) == null;
    }

    @Override
    public long incrementFailure(String appId, long ttlSeconds) {
        String key = "fail:" + appId;
        Entry updated = entries.compute(key, (ignored, entry) -> {
            if (entry == null || entry.expireAt() <= System.currentTimeMillis()) {
                return new Entry(expireAt(ttlSeconds), 1L);
            }
            return new Entry(entry.expireAt(), entry.count() + 1L);
        });
        return updated.count();
    }

    @Override
    public long failureCount(String appId) {
        Entry entry = liveEntry("fail:" + appId);
        return entry == null ? 0L : entry.count();
    }

    private Entry liveEntry(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt() <= System.currentTimeMillis()) {
            entries.remove(key, entry);
            return null;
        }
        return entry;
    }

    private long expireAt(long ttlSeconds) {
        long ttl = ttlSeconds <= 0 ? DEFAULT_TTL_SECONDS : ttlSeconds;
        return System.currentTimeMillis() + ttl * 1000L;
    }

    private record Entry(long expireAt, long count) {

        Entry(long expireAt) {
            this(expireAt, 1L);
        }
    }
}
