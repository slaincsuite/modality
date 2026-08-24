package cn.slainc.redis.cache;

import cn.slainc.core.constant.CacheKeys;
import cn.slainc.redis.autoconfigure.RedisProperties;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

public class CacheTemplate {

    private static final String CACHE_PREFIX = CacheKeys.PREFIX + "cache:";

    private static final Duration MIN_TTL = Duration.ofSeconds(1);

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisProperties properties;

    private final ConcurrentHashMap<String, Object> localLocks = new ConcurrentHashMap<>();

    private final LongAdder hits = new LongAdder();

    private final LongAdder misses = new LongAdder();

    private final LongAdder loads = new LongAdder();

    private final LongAdder nullHits = new LongAdder();

    private final LongAdder degradedLoads = new LongAdder();

    public CacheTemplate(RedisTemplate<String, Object> redisTemplate,
                         StringRedisTemplate stringRedisTemplate,
                         RedisProperties properties) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate 不能为空");
        }
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public <T> T get(String key, Supplier<T> loader) {
        return get(key, properties.getCache().getDefaultTtl(), loader);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Duration ttl, Supplier<T> loader) {
        if (key == null) {
            throw new IllegalArgumentException("缓存键不能为空");
        }
        String redisKey = normalize(key);
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached instanceof NullValueHolder) {
            nullHits.increment();
            return null;
        }
        if (cached != null) {
            hits.increment();
            return (T) cached;
        }
        misses.increment();
        return loadWithSingleFlight(redisKey, ttl, loader);
    }

    @SuppressWarnings("unchecked")
    private <T> T loadWithSingleFlight(String redisKey, Duration ttl, Supplier<T> loader) {
        Object localLock = localLocks.computeIfAbsent(redisKey, k -> new Object());
        try {
            synchronized (localLock) {
                Object cached = redisTemplate.opsForValue().get(redisKey);
                if (cached instanceof NullValueHolder) {
                    nullHits.increment();
                    return null;
                }
                if (cached != null) {
                    hits.increment();
                    return (T) cached;
                }
                String lockKey = redisKey + ":lock";
                String token = UUID.randomUUID().toString();
                long waitMillis = Math.max(properties.getCache().getLockWaitMillis(), 0L);
                long spinMillis = Math.max(properties.getCache().getSpinIntervalMillis(), 1L);
                long deadline = System.currentTimeMillis() + waitMillis;
                boolean locked = acquire(lockKey, token, waitMillis);
                try {
                    if (!locked) {
                        while (System.currentTimeMillis() < deadline) {
                            sleep(spinMillis);
                            Object value = redisTemplate.opsForValue().get(redisKey);
                            if (value instanceof NullValueHolder) {
                                nullHits.increment();
                                return null;
                            }
                            if (value != null) {
                                hits.increment();
                                return (T) value;
                            }
                            locked = acquire(lockKey, token, 1L);
                            if (locked) {
                                break;
                            }
                        }
                    }
                    if (!locked) {
                        degradedLoads.increment();
                    }
                    T value = loader == null ? null : loader.get();
                    loads.increment();
                    putInternal(redisKey, value, ttl);
                    return value;
                } finally {
                    if (locked) {
                        release(lockKey, token);
                    }
                }
            }
        } finally {
            localLocks.remove(redisKey, localLock);
        }
    }

    public void put(String key, Object value) {
        put(key, value, properties.getCache().getDefaultTtl());
    }

    public void put(String key, Object value, Duration ttl) {
        if (key == null) {
            throw new IllegalArgumentException("缓存键不能为空");
        }
        putInternal(normalize(key), value, ttl);
    }

    public boolean remove(String key) {
        if (key == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.delete(normalize(key)));
    }

    public long clear(String prefix) {
        String pattern = normalizePrefix(prefix) + "*";
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(500).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (keys.isEmpty()) {
            return 0L;
        }
        Long removed = stringRedisTemplate.unlink(keys);
        return removed == null ? 0L : removed;
    }

    public boolean exists(String key) {
        return key != null && Boolean.TRUE.equals(redisTemplate.hasKey(normalize(key)));
    }

    public CacheStatsSnapshot stats() {
        return new CacheStatsSnapshot(hits.sum(), misses.sum(), loads.sum(), nullHits.sum(), degradedLoads.sum());
    }

    public void resetStats() {
        hits.reset();
        misses.reset();
        loads.reset();
        nullHits.reset();
        degradedLoads.reset();
    }

    public static String normalize(String key) {
        if (key.startsWith(CacheKeys.PREFIX)) {
            return key;
        }
        return CACHE_PREFIX + key;
    }

    public static String normalizePrefix(String prefix) {
        String value = prefix == null ? "" : prefix;
        if (value.startsWith(CacheKeys.PREFIX)) {
            return value;
        }
        return CACHE_PREFIX + value;
    }

    private void putInternal(String redisKey, Object value, Duration ttl) {
        Duration effective = jitter(ttl == null ? properties.getCache().getDefaultTtl() : ttl);
        if (value == null) {
            redisTemplate.opsForValue().set(redisKey, NullValueHolder.INSTANCE, nullTtl());
            return;
        }
        redisTemplate.opsForValue().set(redisKey, value, effective);
    }

    private Duration nullTtl() {
        Duration ttl = properties.getCache().getNullValueTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? MIN_TTL : ttl;
    }

    private Duration jitter(Duration ttl) {
        double jitter = properties.getCache().getTtlJitter();
        long millis = ttl == null ? 0L : ttl.toMillis();
        if (millis <= 0L) {
            millis = MIN_TTL.toMillis();
        }
        if (jitter <= 0d) {
            return Duration.ofMillis(millis);
        }
        double factor = 1d + jitter * (ThreadLocalRandom.current().nextDouble() * 2d - 1d);
        long jittered = (long) (millis * factor);
        return Duration.ofMillis(Math.max(jittered, MIN_TTL.toMillis()));
    }

    private boolean acquire(String lockKey, String token, long ttlMillis) {
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, Duration.ofMillis(Math.max(ttlMillis, 1L)));
        return Boolean.TRUE.equals(ok);
    }

    private void release(String lockKey, String token) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
