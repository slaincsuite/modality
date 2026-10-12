package cn.slainc.redis.dao;

import cn.slainc.core.constant.CacheKeys;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SaTokenRedisStore {

    public static final long NEVER_EXPIRE = -1L;

    public static final long NOT_VALUE_EXPIRE = -2L;

    private static final String NOT_VALUE = "__NOT_VALUE__";

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    private final int scanCount;

    public SaTokenRedisStore(StringRedisTemplate stringRedisTemplate,
                             RedisTemplate<String, Object> redisTemplate,
                             ObjectMapper objectMapper,
                             int scanCount) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.scanCount = scanCount <= 0 ? 500 : scanCount;
    }

    public String get(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || NOT_VALUE.equals(value)) {
            return null;
        }
        return value;
    }

    public Map<String, String> getMany(List<String> keys) {
        Map<String, String> result = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        if (values == null) {
            return result;
        }
        for (int index = 0; index < keys.size() && index < values.size(); index++) {
            String value = values.get(index);
            if (value != null && !NOT_VALUE.equals(value)) {
                result.put(keys.get(index), value);
            }
        }
        return result;
    }

    public Map<String, Long> getTimeouts(List<String> keys) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        List<Object> values = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.keyCommands().ttl(key.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        for (int index = 0; index < keys.size() && index < values.size(); index++) {
            Object value = values.get(index);
            if (value instanceof Long ttl) {
                result.put(keys.get(index), ttl);
            }
        }
        return result;
    }

    public void set(String key, String value, long timeout) {
        if (timeout <= NOT_VALUE_EXPIRE) {
            return;
        }
        // Sa-Token 约定 timeout <= 0 表示随框架默认，此处按永久写入
        if (timeout == NEVER_EXPIRE || timeout == 0L) {
            stringRedisTemplate.opsForValue().set(key, value);
        } else {
            stringRedisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
        }
    }

    public void update(String key, String value) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            return;
        }
        long timeout = getTimeout(key);
        if (timeout == NOT_VALUE_EXPIRE) {
            return;
        }
        if (timeout == NEVER_EXPIRE) {
            stringRedisTemplate.opsForValue().set(key, value);
            return;
        }
        Long remain = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        // 剩余不足 1 秒时按 1 秒续写，避免 TTL 归零导致立即过期
        long keep = remain == null || remain <= 0 ? 1L : remain;
        stringRedisTemplate.opsForValue().set(key, value, keep, TimeUnit.SECONDS);
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    public long getTimeout(String key) {
        Long expire = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (expire == null || expire == NOT_VALUE_EXPIRE) {
            return NOT_VALUE_EXPIRE;
        }
        if (expire == NEVER_EXPIRE) {
            return NEVER_EXPIRE;
        }
        return expire;
    }

    public void updateTimeout(String key, long timeout) {
        if (timeout == 0L || timeout <= NOT_VALUE_EXPIRE) {
            return;
        }
        if (timeout == NEVER_EXPIRE) {
            stringRedisTemplate.persist(key);
        } else {
            stringRedisTemplate.expire(key, timeout, TimeUnit.SECONDS);
        }
    }

    public Object getObject(String key) {
        return requireObjectTemplate().opsForValue().get(key);
    }

    public <T> T getObject(String key, Class<T> classType) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        }
        if (classType == null || classType.isInstance(value)) {
            return classType == null ? (T) value : classType.cast(value);
        }
        if (objectMapper == null) {
            throw new IllegalStateException("缺少 Jackson ObjectMapper，无法转换 Redis 对象类型：" + classType.getName());
        }
        return objectMapper.convertValue(value, classType);
    }

    public void setObject(String key, Object object, long timeout) {
        RedisTemplate<String, Object> template = requireObjectTemplate();
        if (object == null) {
            template.delete(key);
            return;
        }
        if (timeout <= NOT_VALUE_EXPIRE) {
            return;
        }
        if (timeout == NEVER_EXPIRE || timeout == 0L) {
            template.opsForValue().set(key, object);
        } else {
            template.opsForValue().set(key, object, timeout, TimeUnit.SECONDS);
        }
    }

    public void updateObject(String key, Object object) {
        RedisTemplate<String, Object> template = requireObjectTemplate();
        if (!Boolean.TRUE.equals(template.hasKey(key))) {
            return;
        }
        long timeout = getObjectTimeout(key);
        if (timeout == NOT_VALUE_EXPIRE) {
            return;
        }
        if (timeout == NEVER_EXPIRE) {
            template.opsForValue().set(key, object);
            return;
        }
        Long remain = template.getExpire(key, TimeUnit.SECONDS);
        long keep = remain == null || remain <= 0 ? 1L : remain;
        template.opsForValue().set(key, object, keep, TimeUnit.SECONDS);
    }

    public void deleteObject(String key) {
        requireObjectTemplate().delete(key);
    }

    public long getObjectTimeout(String key) {
        Long expire = requireObjectTemplate().getExpire(key, TimeUnit.SECONDS);
        if (expire == null || expire == NOT_VALUE_EXPIRE) {
            return NOT_VALUE_EXPIRE;
        }
        if (expire == NEVER_EXPIRE) {
            return NEVER_EXPIRE;
        }
        return expire;
    }

    public void updateObjectTimeout(String key, long timeout) {
        RedisTemplate<String, Object> template = requireObjectTemplate();
        if (timeout == 0L || timeout <= NOT_VALUE_EXPIRE) {
            return;
        }
        if (timeout == NEVER_EXPIRE) {
            template.persist(key);
        } else {
            template.expire(key, timeout, TimeUnit.SECONDS);
        }
    }

    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        String safePrefix = prefix == null ? "" : prefix;
        String safeKeyword = keyword == null ? "" : keyword;
        String pattern = safePrefix + "*" + safeKeyword + "*";
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(scanCount).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (sortType) {
            Collections.sort(keys);
        }
        if (size <= 0 || start >= keys.size()) {
            return new ArrayList<>();
        }
        int from = Math.max(start, 0);
        int to = Math.min(from + size, keys.size());
        if (from >= to) {
            return new ArrayList<>();
        }
        return new ArrayList<>(keys.subList(from, to));
    }

    public Map<String, String> hashEntries(String key) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        Map<String, String> result = new java.util.LinkedHashMap<>();
        entries.forEach((k, v) -> result.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
        return result;
    }

    public String storeKey(String suffix) {
        return CacheKeys.CONFIG + suffix;
    }

    private RedisTemplate<String, Object> requireObjectTemplate() {
        if (redisTemplate == null) {
            throw new IllegalStateException("缺少 RedisTemplate，无法执行对象型 Redis 操作");
        }
        return redisTemplate;
    }
}
