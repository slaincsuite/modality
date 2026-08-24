package cn.slainc.redis.key;

import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.support.LettuceNativeCommands;
import cn.slainc.redis.support.PortableRedisCommands;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RedisKeyManager {

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedisProperties properties;

    public RedisKeyManager(StringRedisTemplate stringRedisTemplate,
                           RedisTemplate<String, Object> redisTemplate,
                           RedisProperties properties) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public List<String> scan(String pattern) {
        return scan(pattern, 0);
    }

    public List<String> scan(String pattern, int limit) {
        String match = pattern == null || pattern.isBlank() ? "*" : pattern;
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(match).count(scanCount()).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
                if (limit > 0 && keys.size() >= limit) {
                    break;
                }
            }
        }
        return keys;
    }

    public Set<String> scanSet(String pattern) {
        return new LinkedHashSet<>(scan(pattern, 0));
    }

    public long deleteByPrefix(String prefix) {
        List<String> keys = scan((prefix == null ? "" : prefix) + "*");
        if (keys.isEmpty()) {
            return 0L;
        }
        return unlink(keys.toArray(new String[0]));
    }

    public long delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return 0L;
        }
        Long deleted = stringRedisTemplate.delete(List.of(keys));
        return deleted == null ? 0L : deleted;
    }

    public long unlink(String... keys) {
        if (keys == null || keys.length == 0) {
            return 0L;
        }
        Long deleted = stringRedisTemplate.unlink(List.of(keys));
        return deleted == null ? 0L : deleted;
    }

    public boolean expire(String key, Duration ttl) {
        if (key == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.expire(key, ttl));
    }

    public Duration ttl(String key) {
        Long seconds = stringRedisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
        if (seconds == null || seconds < 0L) {
            return null;
        }
        return Duration.ofSeconds(seconds);
    }

    public Long ttlSeconds(String key) {
        return stringRedisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
    }

    public boolean persist(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.persist(key));
    }

    public Long memoryUsage(String key) {
        if (key == null) {
            return null;
        }
        Long portable = PortableRedisCommands.memoryUsage(stringRedisTemplate, key);
        if (portable != null) {
            return portable;
        }
        byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
        return stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                LettuceNativeCommands.memoryUsage(connection, rawKey));
    }

    public DataType dataType(String key) {
        return stringRedisTemplate.type(key);
    }

    public String type(String key) {
        DataType type = dataType(key);
        return type == null ? "none" : type.code();
    }

    public boolean rename(String key, String newKey) {
        if (key == null || newKey == null) {
            return false;
        }
        stringRedisTemplate.rename(key, newKey);
        return true;
    }

    public boolean renameIfAbsent(String key, String newKey) {
        return Boolean.TRUE.equals(stringRedisTemplate.renameIfAbsent(key, newKey));
    }

    public boolean exists(String key) {
        return key != null && Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    public long touch(String... keys) {
        if (keys == null || keys.length == 0) {
            return 0L;
        }
        Long touched = stringRedisTemplate.execute((RedisCallback<Long>) connection -> {
            byte[][] raw = new byte[keys.length][];
            for (int i = 0; i < keys.length; i++) {
                raw[i] = keys[i].getBytes(StandardCharsets.UTF_8);
            }
            return connection.keyCommands().touch(raw);
        });
        return touched == null ? 0L : touched;
    }

    public long count(String pattern) {
        return scan(pattern, 0).size();
    }

    public long dbSize() {
        Long size = stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                connection.serverCommands().dbSize());
        return size == null ? 0L : size;
    }

    private int scanCount() {
        return properties == null || properties.getKey() == null
                ? 500
                : Math.max(properties.getKey().getScanCount(), 1);
    }
}
