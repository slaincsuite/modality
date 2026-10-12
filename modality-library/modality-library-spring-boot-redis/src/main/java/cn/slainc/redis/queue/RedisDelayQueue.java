package cn.slainc.redis.queue;

import cn.slainc.redis.autoconfigure.RedisProperties;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RedisDelayQueue {

    private static final int REMOVE_BATCH = 500;

    private static final RedisScript<List> POLL_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local items = redis.call('ZRANGEBYSCORE', key, 0, now, 'LIMIT', 0, limit)
            if #items > 0 then
              redis.call('ZREM', key, unpack(items))
            end
            return items
            """, List.class);

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisProperties properties;

    public RedisDelayQueue(StringRedisTemplate stringRedisTemplate, RedisProperties properties) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public void offer(String queue, String payload, java.time.Duration delay) {
        long readyAt = System.currentTimeMillis() + (delay == null ? 0L : Math.max(delay.toMillis(), 0L));
        String member = readyAt + "|" + UUID.randomUUID().toString().replace("-", "")
                + "|" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
        stringRedisTemplate.opsForZSet().add(key(queue), member, readyAt);
    }

    public List<DelayTask> poll(String queue, int batchSize) {
        List<?> members = stringRedisTemplate.execute(POLL_SCRIPT,
                Collections.singletonList(key(queue)),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(Math.max(batchSize, 1)));
        List<DelayTask> result = new ArrayList<>();
        if (members == null) {
            return result;
        }
        for (Object member : members) {
            DelayTask task = parse(String.valueOf(member));
            if (task != null) {
                result.add(task);
            }
        }
        return result;
    }

    public List<DelayTask> poll(String queue) {
        return poll(queue, properties.getQueue().getBatchSize());
    }

    public long size(String queue) {
        Long size = stringRedisTemplate.opsForZSet().zCard(key(queue));
        return size == null ? 0L : size;
    }

    public long remove(String queue, String payload) {
        String target = encode(payload);
        String suffix = "|" + target;
        String redisKey = key(queue);
        List<String> matched = new ArrayList<>();
        long removed = 0L;
        try (Cursor<ZSetOperations.TypedTuple<String>> cursor = stringRedisTemplate.opsForZSet().scan(redisKey,
                ScanOptions.scanOptions().count(scanCount()).build())) {
            while (cursor.hasNext()) {
                ZSetOperations.TypedTuple<String> tuple = cursor.next();
                String member = tuple == null ? null : tuple.getValue();
                if (member != null && member.endsWith(suffix)) {
                    matched.add(member);
                    if (matched.size() >= REMOVE_BATCH) {
                        removed += removeBatch(redisKey, matched);
                    }
                }
            }
        }
        removed += removeBatch(redisKey, matched);
        return removed;
    }

    public long clear(String queue) {
        Boolean deleted = stringRedisTemplate.delete(key(queue));
        return Boolean.TRUE.equals(deleted) ? 1L : 0L;
    }

    private long removeBatch(String redisKey, List<String> matched) {
        if (matched.isEmpty()) {
            return 0L;
        }
        Long removed = stringRedisTemplate.opsForZSet().remove(redisKey, matched.toArray());
        matched.clear();
        return removed == null ? 0L : removed;
    }

    private int scanCount() {
        int count = properties == null ? 0 : properties.getKey().getScanCount();
        return count <= 0 ? 500 : count;
    }

    private String encode(String payload) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
    }

    private DelayTask parse(String member) {
        try {
            int first = member.indexOf('|');
            int second = member.indexOf('|', first + 1);
            if (first < 0 || second < 0) {
                return null;
            }
            long readyAt = Long.parseLong(member.substring(0, first));
            String id = member.substring(first + 1, second);
            String payload = new String(Base64.getUrlDecoder().decode(member.substring(second + 1)),
                    StandardCharsets.UTF_8);
            return new DelayTask(id, payload, readyAt);
        } catch (RuntimeException exception) {
            // 脏成员已由 Lua 侧移除，这里只丢弃解析结果，避免整批 poll 失败
            return null;
        }
    }

    private String key(String queue) {
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("延迟队列的名称不能为空");
        }
        return queue.startsWith("modality:") ? queue : "modality:queue:delay:" + queue;
    }
}
