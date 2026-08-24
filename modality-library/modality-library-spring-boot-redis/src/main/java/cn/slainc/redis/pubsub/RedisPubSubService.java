package cn.slainc.redis.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class RedisPubSubService {

    private static final Logger log = LoggerFactory.getLogger(RedisPubSubService.class);

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisMessageListenerContainer container;

    private final ObjectMapper objectMapper;

    private final Map<String, List<MessageListener>> channelListeners = new ConcurrentHashMap<>();

    private final List<Object[]> patternListeners = new CopyOnWriteArrayList<>();

    public RedisPubSubService(StringRedisTemplate stringRedisTemplate,
                              RedisMessageListenerContainer container,
                              ObjectMapper objectMapper) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        if (container == null) {
            throw new IllegalArgumentException("redisMessageListenerContainer 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.container = container;
        this.objectMapper = objectMapper;
    }

    public long publish(String channel, String payload) {
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("发布频道不能为空");
        }
        Long received = stringRedisTemplate.convertAndSend(channel, payload == null ? "" : payload);
        return received == null ? 0L : received;
    }

    public long publishObject(String channel, Object payload) {
        if (objectMapper == null) {
            throw new IllegalStateException("缺少 Jackson ObjectMapper，无法序列化发布内容");
        }
        try {
            return publish(channel, objectMapper.writeValueAsString(payload));
        } catch (Exception exception) {
            throw new IllegalStateException("发布内容序列化失败：" + exception.getMessage(), exception);
        }
    }

    public void subscribe(String channel, MessageListener listener) {
        if (channel == null || listener == null) {
            return;
        }
        container.addMessageListener(listener, new ChannelTopic(channel));
        channelListeners.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(listener);
        log.info("已订阅 Redis 频道：{}", channel);
    }

    public void subscribePattern(String pattern, MessageListener listener) {
        if (pattern == null || listener == null) {
            return;
        }
        container.addMessageListener(listener, new PatternTopic(pattern));
        patternListeners.add(new Object[]{pattern, listener});
        log.info("已按模式订阅 Redis 频道：{}", pattern);
    }

    public void unsubscribe(String channel) {
        List<MessageListener> listeners = channelListeners.remove(channel);
        if (listeners == null) {
            return;
        }
        for (MessageListener listener : listeners) {
            container.removeMessageListener(listener, new ChannelTopic(channel));
        }
    }

    public void unsubscribeAll() {
        for (String channel : channelListeners.keySet()) {
            unsubscribe(channel);
        }
        for (Object[] entry : patternListeners) {
            container.removeMessageListener((MessageListener) entry[1], new PatternTopic((String) entry[0]));
        }
        patternListeners.clear();
    }

    public int channelListenerCount(String channel) {
        List<MessageListener> listeners = channelListeners.get(channel);
        return listeners == null ? 0 : listeners.size();
    }

    public boolean isRunning() {
        return container.isRunning();
    }
}
