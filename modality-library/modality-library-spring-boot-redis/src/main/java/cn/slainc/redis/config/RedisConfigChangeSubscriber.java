package cn.slainc.redis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

public class RedisConfigChangeSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisConfigChangeSubscriber.class);

    private final RedisDynamicConfigService configService;

    private final ObjectMapper objectMapper;

    public RedisConfigChangeSubscriber(RedisDynamicConfigService configService, ObjectMapper objectMapper) {
        if (configService == null) {
            throw new IllegalArgumentException("RedisDynamicConfigService 不能为空");
        }
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    public String channel() {
        return configService.channel();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            ConfigChangeMessage change = objectMapper.readValue(body, ConfigChangeMessage.class);
            if (change.getNodeId() != null && change.getNodeId().equals(configService.nodeId())) {
                return;
            }
            configService.receive(change);
        } catch (Exception exception) {
            log.warn("动态配置变更消息处理失败，原因={}", exception.getMessage());
        }
    }
}
