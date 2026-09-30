package cn.slainc.modules.auth.store;
import cn.slainc.modules.auth.third.ThirdBindTicket;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

public interface ThirdBindTicketStore {

    void save(ThirdBindTicket ticket, long ttlSeconds);

    Optional<ThirdBindTicket> find(String ticketId);

    void delete(String ticketId);

    static ThirdBindTicketStore of(StringRedisTemplate redis) {
        return redis == null ? new MemoryThirdBindTicketStore() : new RedisThirdBindTicketStore(redis);
    }
}
