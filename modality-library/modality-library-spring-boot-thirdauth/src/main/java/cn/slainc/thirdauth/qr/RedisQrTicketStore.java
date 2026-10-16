package cn.slainc.thirdauth.qr;

import cn.slainc.thirdauth.config.ThirdAuthKeys;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class RedisQrTicketStore implements QrTicketStore {

    private static final String CONSUME_SCRIPT = """
            local value = redis.call('GET', KEYS[1])
            if not value then
                return nil
            end
            local ticket = cjson.decode(value)
            if ticket['status'] ~= ARGV[1] then
                return nil
            end
            redis.call('DEL', KEYS[1])
            return value
            """;

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<String> consumeScript =
            new DefaultRedisScript<>(CONSUME_SCRIPT, String.class);

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

    @Override
    public Optional<QrTicket> consume(String ticketId, QrTicket.Status expected) {
        // 状态判断与删除必须原子完成，否则并发换取会各自拿到同一张已确认票据
        String json = redisTemplate.execute(consumeScript, List.of(ThirdAuthKeys.qrTicketKey(ticketId)),
                expected.name());
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        QrTicket ticket = JsonUtils.parse(json, QrTicket.class);
        if (ticket == null || ticket.expired(System.currentTimeMillis())) {
            return Optional.empty();
        }
        return Optional.of(ticket);
    }
}
