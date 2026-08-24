package cn.slainc.redis.cache;

import cn.slainc.core.constant.CacheKeys;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CacheInvalidationPublisher {

    public static final String CHANNEL = CacheKeys.PREFIX + "cache:invalidate";

    private final StringRedisTemplate stringRedisTemplate;

    public CacheInvalidationPublisher(StringRedisTemplate stringRedisTemplate) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public static String channel() {
        return CHANNEL;
    }

    public void publishInvalidate(String cacheName, String key) {
        send(cacheName, key == null ? "" : key, false);
    }

    public void publishClear(String cacheName) {
        send(cacheName, "", true);
    }

    private void send(String cacheName, String key, boolean clear) {
        String payload = (clear ? "C" : "I") + "|" + encode(cacheName == null ? "" : cacheName) + "|" + encode(key);
        stringRedisTemplate.convertAndSend(CHANNEL, payload);
    }

    static String[] decode(String payload) {
        if (payload == null || payload.length() < 2) {
            return new String[]{"I", "", ""};
        }
        String[] parts = payload.split("\\|", -1);
        if (parts.length < 3) {
            return new String[]{"I", "", ""};
        }
        String action = "C".equals(parts[0]) ? "C" : "I";
        return new String[]{action, decodePart(parts[1]), decodePart(parts[2])};
    }

    private static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
