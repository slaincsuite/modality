package cn.slainc.thirdauth.state;

import cn.slainc.thirdauth.config.ThirdAuthKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

public class RedisStateStore implements StateStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String GET_DEL_SCRIPT = """
            local value = redis.call('GET', KEYS[1])
            if value then
                redis.call('DEL', KEYS[1])
            end
            return value
            """;

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<String> getDelScript = new DefaultRedisScript<>(GET_DEL_SCRIPT, String.class);

    public RedisStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String create(long ttlSeconds) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        redisTemplate.opsForValue().set(ThirdAuthKeys.stateKey(state), "1",
                Duration.ofSeconds(Math.max(ttlSeconds, 1)));
        return state;
    }

    @Override
    public void put(String state, long ttlSeconds) {
        if (state != null && !state.isBlank()) {
            redisTemplate.opsForValue().set(ThirdAuthKeys.stateKey(state), "1",
                    Duration.ofSeconds(Math.max(ttlSeconds, 1)));
        }
    }

    @Override
    public boolean consume(String state) {
        if (state == null) {
            return false;
        }
        String value = redisTemplate.execute(getDelScript, List.of(ThirdAuthKeys.stateKey(state)));
        return value != null;
    }
}
