package cn.slainc.web.websocket;

import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class RedisWebSocketBroadcaster implements WebSocketBroadcaster, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RedisWebSocketBroadcaster.class);

    private final StringRedisTemplate template;

    private final LocalWebSocketBroadcaster local;

    private final String channel;

    private final String nodeId = UUID.randomUUID().toString();

    private RedisMessageListenerContainer container;

    public RedisWebSocketBroadcaster(StringRedisTemplate template, LocalWebSocketBroadcaster local, String channel) {
        this.template = template;
        this.local = local;
        this.channel = channel;
    }

    @Override
    public void afterPropertiesSet() {
        RedisConnectionFactory connectionFactory = template.getConnectionFactory();
        if (connectionFactory == null) {
            log.warn("未找到 RedisConnectionFactory，WebSocket 集群广播降级为单机广播，节点={}", nodeId);
            return;
        }
        RedisMessageListenerContainer listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(connectionFactory);
        listenerContainer.addMessageListener(this::onMessage, new ChannelTopic(channel));
        listenerContainer.afterPropertiesSet();
        try {
            listenerContainer.start();
        } catch (RuntimeException exception) {
            log.warn("WebSocket 集群广播订阅失败，当前降级为单机广播，节点={}：{}", nodeId, exception.getMessage());
            this.container = listenerContainer;
            return;
        }
        this.container = listenerContainer;
        log.info("WebSocket 集群广播已启用：频道={} 节点={}", channel, nodeId);
    }

    @Override
    public void broadcast(String topic, String payload) {
        publish(WebSocketBroadcast.topic(topic, payload, nodeId));
    }

    @Override
    public void sendToUser(String userId, String payload) {
        publish(WebSocketBroadcast.user(userId, payload, nodeId));
    }

    @Override
    public boolean sendToSession(String sessionId, String payload) {
        publish(WebSocketBroadcast.session(sessionId, payload, nodeId));
        return true;
    }

    @Override
    public void destroy() {
        if (container == null) {
            return;
        }
        try {
            container.destroy();
        } catch (Exception exception) {
            log.warn("WebSocket 集群广播停止失败：{}", exception.getMessage());
        }
        log.info("WebSocket 集群广播已停止：节点={}", nodeId);
    }

    public String nodeId() {
        return nodeId;
    }

    public String channel() {
        return channel;
    }

    private void publish(WebSocketBroadcast broadcast) {
        try {
            template.convertAndSend(channel, JsonUtils.toJson(broadcast));
        } catch (RuntimeException exception) {
            log.warn("WebSocket 广播投递到 Redis 失败，降级为本地投递：{}", exception.getMessage());
        }
        deliver(broadcast);
    }

    private void onMessage(Message message, byte[] pattern) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        WebSocketBroadcast broadcast = WebSocketBroadcast.parse(json);
        if (broadcast == null) {
            log.warn("忽略无法解析的 WebSocket 广播消息：{}", json);
            return;
        }
        if (nodeId.equals(broadcast.nodeId())) {
            return;
        }
        log.debug("收到其他节点的 WebSocket 广播：来自节点={} 类型={}", broadcast.nodeId(), broadcast.type());
        deliver(broadcast);
    }

    private void deliver(WebSocketBroadcast broadcast) {
        String type = broadcast.type();
        if (type == null) {
            log.warn("忽略缺少类型的 WebSocket 广播消息");
            return;
        }
        switch (type) {
            case WebSocketBroadcast.TYPE_TOPIC -> local.broadcast(broadcast.topic(), broadcast.payload());
            case WebSocketBroadcast.TYPE_USER -> local.sendToUser(broadcast.userId(), broadcast.payload());
            case WebSocketBroadcast.TYPE_SESSION -> local.sendToSession(broadcast.sessionId(), broadcast.payload());
            default -> log.warn("忽略未知类型的 WebSocket 广播消息：类型={}", type);
        }
    }
}
