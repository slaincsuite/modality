package cn.slainc.oauth2.state;

import cn.slainc.oauth2.config.Oauth2Keys;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StateService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    private final long timeoutSeconds;

    private final Map<String, Long> localStore = new ConcurrentHashMap<>();

    public StateService(StringRedisTemplate redisTemplate, long timeoutSeconds) {
        this.redisTemplate = redisTemplate;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 300;
    }

    public String issue() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        String key = Oauth2Keys.stateKey(state);
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(key, "1", java.time.Duration.ofSeconds(timeoutSeconds));
        } else {
            localStore.put(key, System.currentTimeMillis() + timeoutSeconds * 1000);
        }
        return state;
    }

    public boolean validateOnce(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String key = Oauth2Keys.stateKey(state);
        if (redisTemplate != null) {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        }
        Long expireAt = localStore.remove(key);
        if (expireAt == null) {
            return false;
        }
        return expireAt > System.currentTimeMillis();
    }
}
