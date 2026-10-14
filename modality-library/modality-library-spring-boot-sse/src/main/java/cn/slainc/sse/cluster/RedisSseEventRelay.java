package cn.slainc.sse.cluster;

import cn.slainc.redis.pubsub.RedisPubSubService;
import cn.slainc.sse.support.SseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisSseEventRelay implements SseEventRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSseEventRelay.class);

    private final RedisPubSubService pubSubService;

    private final String topic;

    private final String nodeId;

    public RedisSseEventRelay(RedisPubSubService pubSubService, String topic, String nodeId) {
        this.pubSubService = pubSubService;
        this.topic = topic;
        this.nodeId = nodeId;
    }

    @Override
    public void publish(Long userId, SseEvent event) {
        if (userId == null || event == null) {
            return;
        }
        try {
            pubSubService.publishObject(topic, new SseClusterMessage(nodeId, userId, event));
        } catch (RuntimeException e) {
            LOGGER.warn("SSE 事件转发到 Redis 失败：eventId={}，原因={}", event.id(), e.getMessage());
        }
    }

    @Override
    public boolean remote() {
        return true;
    }

    public String topic() {
        return topic;
    }

    public String nodeId() {
        return nodeId;
    }
}
