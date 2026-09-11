package cn.slainc.captcha.store;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MemoryCaptchaStore implements CaptchaStore {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(300);

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value, Duration ttl) {
        entries.put(key, new Entry(value, expireAt(ttl)));
    }

    @Override
    public String get(String key) {
        Entry entry = liveEntry(key);
        return entry == null ? null : entry.value();
    }

    @Override
    public void remove(String key) {
        entries.remove(key);
    }

    @Override
    public long failCount(String key) {
        Entry entry = liveEntry(key);
        return entry == null ? 0L : parse(entry.value());
    }

    @Override
    public long incrementFail(String key, Duration ttl) {
        Entry updated = entries.compute(key, (ignored, entry) -> {
            if (entry == null || entry.expireAt() <= System.currentTimeMillis()) {
                return new Entry("1", expireAt(ttl));
            }
            return new Entry(String.valueOf(parse(entry.value()) + 1L), entry.expireAt());
        });
        return parse(updated.value());
    }

    @Override
    public boolean isLocked(String key) {
        return liveEntry(key) != null;
    }

    @Override
    public void lock(String key, Duration ttl) {
        entries.putIfAbsent(key, new Entry("1", expireAt(ttl)));
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

    private long expireAt(Duration ttl) {
        Duration effective = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
        return System.currentTimeMillis() + effective.toMillis();
    }

    private long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private record Entry(String value, long expireAt) {
    }
}
