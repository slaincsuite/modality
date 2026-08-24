package cn.slainc.redis.structure;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;
import java.util.function.Consumer;

public class RedisPipeline {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisPipeline(RedisTemplate<String, Object> redisTemplate) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate 不能为空");
        }
        this.redisTemplate = redisTemplate;
    }

    public List<Object> execute(Consumer<RedisOperations<String, Object>> actions) {
        if (actions == null) {
            throw new IllegalArgumentException("管道命令集合不能为空");
        }
        return redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) {
                actions.accept(operations);
                return null;
            }
        });
    }

    public List<Object> execute(Consumer<RedisOperations<String, Object>> actions,
                                RedisSerializer<?> resultSerializer) {
        if (actions == null) {
            throw new IllegalArgumentException("管道命令集合不能为空");
        }
        return redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public Object execute(RedisOperations operations) {
                actions.accept(operations);
                return null;
            }
        }, resultSerializer);
    }
}
