package cn.slainc.redis.structure;

import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class RedisBitMap {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisBitMap(RedisTemplate<String, Object> redisTemplate) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate 不能为空");
        }
        this.redisTemplate = redisTemplate;
    }

    public boolean setBit(String key, long offset, boolean value) {
        Boolean previous = redisTemplate.opsForValue().setBit(key, offset, value);
        return Boolean.TRUE.equals(previous);
    }

    public boolean getBit(String key, long offset) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(key, offset));
    }

    public long bitCount(String key) {
        Long count = redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().bitCount(bytes(key)));
        return count == null ? 0L : count;
    }

    public long bitCount(String key, long start, long end) {
        Long count = redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().bitCount(bytes(key), start, end));
        return count == null ? 0L : count;
    }

    public long bitOp(RedisStringCommands.BitOperation operation, String destinationKey, String... sourceKeys) {
        byte[][] keys = new byte[sourceKeys.length][];
        for (int i = 0; i < sourceKeys.length; i++) {
            keys[i] = bytes(sourceKeys[i]);
        }
        Long result = redisTemplate.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().bitOp(operation, bytes(destinationKey), keys));
        return result == null ? 0L : result;
    }

    public List<Long> bitField(String key, BitFieldSubCommands commands) {
        return redisTemplate.opsForValue().bitField(key, commands);
    }

    private static byte[] bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
