package cn.slainc.redis.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheInvalidationListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);

    private final Map<String, Cache<String, Object>> localCaches = new ConcurrentHashMap<>();

    public static String channel() {
        return CacheInvalidationPublisher.channel();
    }

    public void register(String cacheName, Cache<String, Object> cache) {
        if (cacheName == null || cache == null) {
            return;
        }
        localCaches.put(cacheName, cache);
    }

    public void unregister(String cacheName) {
        if (cacheName != null) {
            localCaches.remove(cacheName);
        }
    }

    public int registeredCount() {
        return localCaches.size();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        try {
            String[] parts = CacheInvalidationPublisher.decode(new String(message.getBody(), StandardCharsets.UTF_8));
            Cache<String, Object> cache = localCaches.get(parts[1]);
            if (cache == null) {
                return;
            }
            if ("C".equals(parts[0]) || parts[2].isEmpty()) {
                cache.invalidateAll();
            } else {
                cache.invalidate(parts[2]);
            }
        } catch (Exception exception) {
            log.warn("本地缓存失效消息处理失败，原因={}", exception.getMessage());
        }
    }
}
