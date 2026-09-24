package cn.slainc.modules.auth.qrcode;

import cn.slainc.utils.json.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisQrLoginTicketStore implements QrLoginTicketStore {

    public static final String KEY_PREFIX = "modality:auth:qr:";

    private final StringRedisTemplate redis;

    public RedisQrLoginTicketStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(QrLoginTicket ticket, long ttlSeconds) {
        redis.opsForValue().set(KEY_PREFIX + ticket.getTicketId(), JsonUtils.toJson(ticket),
                Duration.ofSeconds(Math.max(1, ttlSeconds)));
    }

    @Override
    public Optional<QrLoginTicket> find(String ticketId) {
        String raw = redis.opsForValue().get(KEY_PREFIX + ticketId);
        return raw == null ? Optional.empty() : Optional.ofNullable(JsonUtils.parse(raw, QrLoginTicket.class));
    }

    @Override
    public void delete(String ticketId) {
        redis.delete(KEY_PREFIX + ticketId);
    }
}
