package cn.slainc.sse.cluster;

import cn.slainc.redis.pubsub.RedisPubSubService;
import cn.slainc.sse.api.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

public class SseClusterSubscriber implements MessageListener, InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(SseClusterSubscriber.class);

    private final RedisPubSubService pubSubService;

    private final SseService sseService;

    private final String topic;

    private final String nodeId;

    public SseClusterSubscriber(RedisPubSubService pubSubService, SseService sseService, String topic, String nodeId) {
        this.pubSubService = pubSubService;
        this.sseService = sseService;
        this.topic = topic;
        this.nodeId = nodeId;
    }

    @Override
    public void afterPropertiesSet() {
        pubSubService.subscribe(topic, this);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        SseClusterMessage clusterMessage = SseClusterMessage.parse(new String(message.getBody(), StandardCharsets.UTF_8));
        if (clusterMessage == null) {
            LOGGER.debug("忽略无法解析的 SSE 集群消息：topic={}", topic);
            return;
        }
        if (nodeId != null && nodeId.equals(clusterMessage.origin())) {
            return;
        }
        sseService.deliver(clusterMessage.userId(), clusterMessage.event());
    }

    @Override
    public void destroy() {
        pubSubService.unsubscribe(topic);
    }

    public String topic() {
        return topic;
    }

    public String nodeId() {
        return nodeId;
    }
}
