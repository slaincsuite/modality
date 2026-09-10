package cn.slainc.jwt.jwks;

import cn.slainc.crypto.jwt.Jwk;
import cn.slainc.crypto.jwt.JwkSet;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.key.JwtKeyEntry;
import cn.slainc.jwt.key.JwtKeyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class JwksService {

    public static final String CACHE_KEY = "modality:jwt:jwks";

    private static final Logger log = LoggerFactory.getLogger(JwksService.class);

    private final JwtKeyManager keyManager;

    private final JwtSettings settings;

    private final StringRedisTemplate redisTemplate;

    private final AtomicLong computeCount = new AtomicLong();

    private volatile String memoryCache;

    public JwksService(JwtKeyManager keyManager, JwtSettings settings, StringRedisTemplate redisTemplate) {
        this.keyManager = keyManager;
        this.settings = settings;
        this.redisTemplate = redisTemplate;
        keyManager.onRotation(this::invalidate);
    }

    public String getJwksJson() {
        String cached = memoryCache;
        if (cached != null) {
            return cached;
        }
        if (redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get(CACHE_KEY);
                if (json != null && !json.isBlank()) {
                    memoryCache = json;
                    return json;
                }
            } catch (RuntimeException exception) {
                log.warn("读取 JWKS 的 Redis 缓存失败，已降级为内存直取", exception);
            }
        }
        String json = compute();
        memoryCache = json;
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(CACHE_KEY, json, Duration.ofSeconds(settings.jwksCacheSeconds()));
            } catch (RuntimeException exception) {
                log.warn("写入 JWKS 的 Redis 缓存失败，仅保留内存结果", exception);
            }
        }
        return json;
    }

    public void invalidate() {
        memoryCache = null;
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(CACHE_KEY);
            } catch (RuntimeException exception) {
                log.warn("清除 JWKS 的 Redis 缓存失败，内存缓存已清空", exception);
            }
        }
    }

    public long computeCount() {
        return computeCount.get();
    }

    private String compute() {
        computeCount.incrementAndGet();
        List<JwtKeyEntry> entries = keyManager.publicExportableEntries();
        List<Jwk> keys = new ArrayList<>(entries.size());
        for (JwtKeyEntry entry : entries) {
            keys.add(entry.publicJwk());
        }
        return new JwkSet(keys).toJson();
    }
}
