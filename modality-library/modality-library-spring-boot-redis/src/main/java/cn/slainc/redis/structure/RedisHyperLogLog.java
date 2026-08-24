package cn.slainc.redis.structure;

import org.springframework.data.redis.core.RedisTemplate;

public class RedisHyperLogLog {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisHyperLogLog(RedisTemplate<String, Object> redisTemplate) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate 不能为空");
        }
        this.redisTemplate = redisTemplate;
    }

    public long add(String key, Object... values) {
        Long added = redisTemplate.opsForHyperLogLog().add(key, values);
        return added == null ? 0L : added;
    }

    public long size(String... keys) {
        Long size = redisTemplate.opsForHyperLogLog().size(keys);
        return size == null ? 0L : size;
    }

    public long union(String destinationKey, String... sourceKeys) {
        Long size = redisTemplate.opsForHyperLogLog().union(destinationKey, sourceKeys);
        return size == null ? 0L : size;
    }

    public void delete(String key) {
        redisTemplate.opsForHyperLogLog().delete(key);
    }
}
