package cn.slainc.redis.cache;

import cn.slainc.core.constant.CacheKeys;
import cn.slainc.redis.autoconfigure.RedisProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class TwoLevelCache {

    private static final Duration MIN_TTL = Duration.ofSeconds(1);

    private final RedisProperties properties;

    private final CacheTemplate cacheTemplate;

    private final CacheInvalidationPublisher publisher;

    private final CacheInvalidationListener invalidationListener;

    private final Map<String, Cache<String, Object>> localCaches = new ConcurrentHashMap<>();

    public TwoLevelCache(RedisTemplate<String, Object> redisTemplate,
                         StringRedisTemplate stringRedisTemplate,
                         RedisProperties properties,
                         CacheInvalidationListener invalidationListener) {
        if (properties == null) {
            throw new IllegalArgumentException("RedisProperties 不能为空");
        }
        this.properties = properties;
        this.cacheTemplate = new CacheTemplate(redisTemplate, stringRedisTemplate, properties);
        this.publisher = new CacheInvalidationPublisher(stringRedisTemplate);
        this.invalidationListener = invalidationListener;
    }

    public CacheTemplate getCacheTemplate() {
        return cacheTemplate;
    }

    public <T> T get(String cacheName, String key, Supplier<T> loader) {
        return get(cacheName, key, properties.getCache().getDefaultTtl(), loader);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Duration ttl, Supplier<T> loader) {
        Cache<String, Object> local = localCache(cacheName);
        if (local != null) {
            Object cached = local.getIfPresent(key);
            if (cached instanceof LocalEntry entry) {
                if (entry.expired(System.nanoTime())) {
                    local.invalidate(key);
                } else if (entry.value() instanceof NullValueHolder) {
                    return null;
                } else {
                    return (T) entry.value();
                }
            }
        }
        T value = cacheTemplate.get(redisKey(cacheName, key), ttl, loader);
        if (local != null) {
            local.put(key, localEntry(value, ttl));
        }
        return value;
    }

    public void put(String cacheName, String key, Object value, Duration ttl) {
        Cache<String, Object> local = localCache(cacheName);
        if (local != null) {
            local.put(key, localEntry(value, ttl));
        }
        cacheTemplate.put(redisKey(cacheName, key), value, ttl);
        publisher.publishInvalidate(cacheName, key);
    }

    public void remove(String cacheName, String key) {
        Cache<String, Object> local = localCache(cacheName);
        if (local != null) {
            local.invalidate(key);
        }
        cacheTemplate.remove(redisKey(cacheName, key));
        publisher.publishInvalidate(cacheName, key);
    }

    public void clear(String cacheName) {
        Cache<String, Object> local = localCache(cacheName);
        if (local != null) {
            local.invalidateAll();
        }
        cacheTemplate.clear(redisKey(cacheName, ""));
        publisher.publishClear(cacheName);
    }

    public CacheStats localStats(String cacheName) {
        Cache<String, Object> local = localCaches.get(cacheName);
        return local == null ? CacheStats.empty() : local.stats();
    }

    public long localSize(String cacheName) {
        Cache<String, Object> local = localCaches.get(cacheName);
        return local == null ? 0L : local.estimatedSize();
    }

    public static String redisKey(String cacheName, String key) {
        return CacheKeys.PREFIX + "cache:" + cacheName + ":" + (key == null ? "" : key);
    }

    private LocalEntry localEntry(Object value, Duration ttl) {
        return new LocalEntry(value == null ? NullValueHolder.INSTANCE : value,
                System.nanoTime() + effectiveTtlNanos(ttl));
    }

    private long effectiveTtlNanos(Duration ttl) {
        Duration requested = ttl == null || ttl.isZero() || ttl.isNegative()
                ? properties.getCache().getDefaultTtl()
                : ttl;
        Duration max = properties.getCache().getSecondLevelTtl();
        Duration effective = max != null && !max.isZero() && !max.isNegative() && max.compareTo(requested) < 0
                ? max
                : requested;
        if (effective == null || effective.isZero() || effective.isNegative()) {
            effective = MIN_TTL;
        }
        return effective.toNanos();
    }

    private long remainingNanos(Object value, long currentNanos) {
        if (value instanceof LocalEntry entry) {
            return Math.max(entry.expireAtNanos() - currentNanos, 0L);
        }
        return effectiveTtlNanos(properties.getCache().getSecondLevelTtl());
    }

    private Cache<String, Object> localCache(String cacheName) {
        if (!properties.getCache().isSecondLevelEnabled() || cacheName == null) {
            return null;
        }
        return localCaches.computeIfAbsent(cacheName, name -> {
            Cache<String, Object> cache = Caffeine.newBuilder()
                    .maximumSize(Math.max(properties.getCache().getSecondLevelMaxSize(), 1L))
                    .expireAfter(new EntryExpiry())
                    .recordStats()
                    .build();
            if (invalidationListener != null) {
                invalidationListener.register(name, cache);
            }
            return cache;
        });
    }

    private record LocalEntry(Object value, long expireAtNanos) {

        boolean expired(long nowNanos) {
            return nowNanos - expireAtNanos >= 0L;
        }
    }

    private final class EntryExpiry implements Expiry<String, Object> {

        @Override
        public long expireAfterCreate(String key, Object value, long currentTime) {
            return remainingNanos(value, currentTime);
        }

        @Override
        public long expireAfterUpdate(String key, Object value, long currentTime, long currentDuration) {
            return remainingNanos(value, currentTime);
        }

        @Override
        public long expireAfterRead(String key, Object value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
