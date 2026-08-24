package cn.slainc.redis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

public class RedisConfigBroadcaster implements ConfigBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RedisConfigBroadcaster.class);

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private final String channel;

    public RedisConfigBroadcaster(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper, String channel) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.channel = channel;
    }

    @Override
    public void publish(ConfigChangeMessage message) {
        if (message == null) {
            return;
        }
        try {
            stringRedisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(message));
        } catch (Exception exception) {
            log.warn("动态配置变更广播失败，键={}，原因={}", message.getKey(), exception.getMessage());
        }
    }

    @Override
    public String channel() {
        return channel;
    }
}
