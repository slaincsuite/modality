package cn.slainc.modules.auth.third;

import cn.slainc.utils.json.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisThirdBindTicketStore implements ThirdBindTicketStore {

    public static final String KEY_PREFIX = "modality:auth:bind:";

    private final StringRedisTemplate redis;

    public RedisThirdBindTicketStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(ThirdBindTicket ticket, long ttlSeconds) {
        redis.opsForValue().set(KEY_PREFIX + ticket.getTicketId(), JsonUtils.toJson(ticket),
                Duration.ofSeconds(Math.max(1, ttlSeconds)));
    }

    @Override
    public Optional<ThirdBindTicket> find(String ticketId) {
        String raw = redis.opsForValue().get(KEY_PREFIX + ticketId);
        return raw == null ? Optional.empty() : Optional.ofNullable(JsonUtils.parse(raw, ThirdBindTicket.class));
    }

    @Override
    public void delete(String ticketId) {
        redis.delete(KEY_PREFIX + ticketId);
    }
}
