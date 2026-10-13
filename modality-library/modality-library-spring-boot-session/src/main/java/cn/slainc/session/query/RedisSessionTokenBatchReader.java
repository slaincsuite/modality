package cn.slainc.session.query;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RedisSessionTokenBatchReader implements SessionTokenBatchReader {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisSessionTokenBatchReader(StringRedisTemplate stringRedisTemplate) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
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
            if (value != null) {
                result.put(keys.get(index), value);
            }
        }
        return result;
    }

    @Override
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
}
