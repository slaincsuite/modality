package cn.slainc.modules.auth.store;

import cn.slainc.utils.json.JsonUtils;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public final class AuthTicketStore<T> {

    public static final String QR_KEY_PREFIX = "modality:auth:qr:";

    public static final String BIND_KEY_PREFIX = "modality:auth:bind:";

    private final StringRedisTemplate redis;

    private final String keyPrefix;

    private final Function<T, String> ticketId;

    private final Class<T> type;

    private final Map<String, MemoryEntry<T>> memory = new ConcurrentHashMap<>();

    private record MemoryEntry<T>(T ticket, long expireAt) {

        boolean expired(long now) {
            return now >= expireAt;
        }
    }

    private AuthTicketStore(StringRedisTemplate redis, String keyPrefix, Function<T, String> ticketId,
            Class<T> type) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
        this.ticketId = ticketId;
        this.type = type;
    }

    public static <T> AuthTicketStore<T> of(StringRedisTemplate redis, String keyPrefix,
            Function<T, String> ticketId, Class<T> type) {
        return new AuthTicketStore<>(redis, keyPrefix, ticketId, type);
    }

    public void save(T ticket, long ttlSeconds) {
        long ttl = Math.max(1, ttlSeconds);
        String id = ticketId.apply(ticket);
        if (redis == null) {
            memory.put(id, new MemoryEntry<>(ticket, System.currentTimeMillis() + ttl * 1000));
            return;
        }
        redis.opsForValue().set(keyPrefix + id, JsonUtils.toJson(ticket), Duration.ofSeconds(ttl));
    }

    public Optional<T> find(String id) {
        if (redis == null) {
            MemoryEntry<T> entry = memory.get(id);
            if (entry == null) {
                return Optional.empty();
            }
            if (entry.expired(System.currentTimeMillis())) {
                memory.remove(id);
                return Optional.empty();
            }
            return Optional.of(entry.ticket());
        }
        String raw = redis.opsForValue().get(keyPrefix + id);
        return Optional.ofNullable(raw).map(this::parse);
    }

    public void delete(String id) {
        if (redis == null) {
            memory.remove(id);
            return;
        }
        redis.delete(keyPrefix + id);
    }

    public Optional<T> consume(String id) {
        if (redis == null) {
            MemoryEntry<T> entry = memory.remove(id);
            if (entry == null || entry.expired(System.currentTimeMillis())) {
                return Optional.empty();
            }
            return Optional.of(entry.ticket());
        }
        String raw = redis.opsForValue().getAndDelete(keyPrefix + id);
        return Optional.ofNullable(raw).map(this::parse);
    }

    public boolean compareAndSet(String id, Predicate<T> guard, T updated, long ttlSeconds) {
        long now = System.currentTimeMillis();
        long ttl = Math.max(1, ttlSeconds);
        if (redis == null) {
            boolean[] replaced = {false};
            memory.compute(id, (key, entry) -> {
                if (entry == null || entry.expired(now) || !guard.test(entry.ticket())) {
                    return entry;
                }
                replaced[0] = true;
                return new MemoryEntry<>(updated, now + ttl * 1000);
            });
            return replaced[0];
        }
        // Redis 侧用 WATCH/MULTI/EXEC 做真正的条件替换：守卫不成立或被并发改写时 exec 返回 null
        List<Object> results = redis.execute(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                String key = keyPrefix + id;
                ops.watch(key);
                String raw = ops.opsForValue().get(key);
                T current = raw == null ? null : parse(raw);
                if (current == null || !guard.test(current)) {
                    ops.unwatch();
                    return List.of();
                }
                ops.multi();
                ops.opsForValue().set(key, JsonUtils.toJson(updated), Duration.ofSeconds(ttl));
                return ops.exec();
            }
        });
        return results != null && !results.isEmpty();
    }

    private T parse(String raw) {
        return JsonUtils.parse(raw, type);
    }
}
