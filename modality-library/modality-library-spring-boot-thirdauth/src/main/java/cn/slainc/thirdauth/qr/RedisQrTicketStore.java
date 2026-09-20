package cn.slainc.thirdauth.qr;

import cn.slainc.thirdauth.config.ThirdAuthKeys;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisQrTicketStore implements QrTicketStore {

    private final StringRedisTemplate redisTemplate;

    public RedisQrTicketStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(QrTicket ticket, long ttlSeconds) {
        redisTemplate.opsForValue().set(ThirdAuthKeys.qrTicketKey(ticket.getTicketId()),
                JsonUtils.toJson(ticket), Duration.ofSeconds(Math.max(ttlSeconds, 1)));
    }

    @Override
    public Optional<QrTicket> find(String ticketId) {
        String json = redisTemplate.opsForValue().get(ThirdAuthKeys.qrTicketKey(ticketId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        QrTicket ticket = JsonUtils.parse(json, QrTicket.class);
        if (ticket == null || ticket.expired(System.currentTimeMillis())) {
            redisTemplate.delete(ThirdAuthKeys.qrTicketKey(ticketId));
            return Optional.empty();
        }
        return Optional.of(ticket);
    }
}
