package cn.slainc.modules.auth.store;

import cn.slainc.utils.json.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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

    private T parse(String raw) {
        return JsonUtils.parse(raw, type);
    }
}
