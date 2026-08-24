package cn.slainc.redis.cache;

import cn.slainc.core.constant.CacheKeys;
import cn.slainc.redis.autoconfigure.RedisProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class TwoLevelCache {

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
            if (cached instanceof NullValueHolder) {
                return null;
            }
            if (cached != null) {
                return (T) cached;
            }
        }
        T value = cacheTemplate.get(redisKey(cacheName, key), ttl, loader);
        if (local != null) {
            local.put(key, value == null ? NullValueHolder.INSTANCE : value);
        }
        return value;
    }

    public void put(String cacheName, String key, Object value, Duration ttl) {
        Cache<String, Object> local = localCache(cacheName);
        if (local != null) {
            local.put(key, value == null ? NullValueHolder.INSTANCE : value);
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

    private Cache<String, Object> localCache(String cacheName) {
        if (!properties.getCache().isSecondLevelEnabled() || cacheName == null) {
            return null;
        }
        return localCaches.computeIfAbsent(cacheName, name -> {
            Cache<String, Object> cache = Caffeine.newBuilder()
                    .maximumSize(Math.max(properties.getCache().getSecondLevelMaxSize(), 1L))
                    .expireAfterWrite(properties.getCache().getSecondLevelTtl())
                    .recordStats()
                    .build();
            if (invalidationListener != null) {
                invalidationListener.register(name, cache);
            }
            return cache;
        });
    }
}
