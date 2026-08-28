package cn.slainc.idempotent.store;

import cn.slainc.idempotent.support.IdempotentKeys;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class LocalIdempotentStore implements IdempotentStore {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private static final long PURGE_INTERVAL_MILLIS = 60_000L;

    private static final int PURGE_THRESHOLD = 256;

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Failure> failures = new ConcurrentHashMap<>();

    private final AtomicBoolean purging = new AtomicBoolean();

    private volatile long lastPurge = System.currentTimeMillis();

    @Override
    public String name() {
        return "local";
    }

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        long expireAt = expireAt(now, ttl);
        AtomicBoolean acquired = new AtomicBoolean();
        entries.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expired(now)) {
                acquired.set(true);
                return new Entry(IdempotentRecord.pending(), expireAt);
            }
            return existing;
        });
        purgeIfNeeded(now);
        return acquired.get();
    }

    @Override
    public void release(String key) {
        entries.remove(key);
    }

    @Override
    public boolean isHeld(String key) {
        Entry entry = entries.get(key);
        return entry != null && !entry.expired(System.currentTimeMillis());
    }

    @Override
    public Optional<IdempotentRecord> findRecord(String key) {
        Entry entry = entries.get(key);
        if (entry == null || entry.expired(System.currentTimeMillis()) || entry.record().isPending()) {
            return Optional.empty();
        }
        return Optional.of(entry.record());
    }

    @Override
    public void saveRecord(String key, IdempotentRecord record, Duration ttl) {
        long now = System.currentTimeMillis();
        entries.put(key, new Entry(record, expireAt(now, ttl)));
    }

    @Override
    public long increaseFailure(String key, Duration window) {
        long now = System.currentTimeMillis();
        long expireAt = expireAt(now, window);
        AtomicReference<Long> counter = new AtomicReference<>(0L);
        failures.compute(IdempotentKeys.failureKey(key), (ignored, existing) -> {
            long count = existing == null || existing.expired(now) ? 1L : existing.count() + 1L;
            counter.set(count);
            return new Failure(count, expireAt);
        });
        purgeIfNeeded(now);
        return counter.get();
    }

    @Override
    public long failureCount(String key) {
        Failure failure = failures.get(IdempotentKeys.failureKey(key));
        if (failure == null) {
            return 0L;
        }
        if (failure.expired(System.currentTimeMillis())) {
            failures.remove(IdempotentKeys.failureKey(key));
            return 0L;
        }
        return failure.count();
    }

    @Override
    public void clearFailure(String key) {
        failures.remove(IdempotentKeys.failureKey(key));
    }

    @Override
    public void saveToken(String token, String owner, Duration ttl) {
        long now = System.currentTimeMillis();
        entries.put(IdempotentKeys.tokenKey(token), new Entry(IdempotentRecord.token(owner), expireAt(now, ttl)));
    }

    @Override
    public String consumeToken(String token) {
        long now = System.currentTimeMillis();
        String tokenKey = IdempotentKeys.tokenKey(token);
        AtomicReference<String> owner = new AtomicReference<>();
        entries.compute(tokenKey, (ignored, existing) -> {
            if (existing == null || existing.expired(now) || existing.record().state() != IdempotentState.TOKEN) {
                return existing == null || existing.expired(now) ? null : existing;
            }
            owner.set(existing.record().payload());
            return null;
        });
        return owner.get();
    }

    @Override
    public void clear(String key) {
        entries.remove(key);
        failures.remove(IdempotentKeys.failureKey(key));
    }

    public int size() {
        purgeIfNeeded(System.currentTimeMillis());
        return entries.size();
    }

    private void purgeIfNeeded(long now) {
        if (entries.size() + failures.size() < PURGE_THRESHOLD) {
            return;
        }
        if (now - lastPurge < PURGE_INTERVAL_MILLIS || !purging.compareAndSet(false, true)) {
            return;
        }
        try {
            lastPurge = now;
            entries.entrySet().removeIf(entry -> entry.getValue().expired(now));
            failures.entrySet().removeIf(failure -> failure.getValue().expired(now));
        } finally {
            purging.set(false);
        }
    }

    private static long expireAt(long now, Duration ttl) {
        Duration actual = ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_TTL : ttl;
        return now + actual.toMillis();
    }

    private record Entry(IdempotentRecord record, long expireAt) {

        boolean expired(long now) {
            return expireAt <= now;
        }
    }

    private record Failure(long count, long expireAt) {

        boolean expired(long now) {
            return expireAt <= now;
        }
    }
}
