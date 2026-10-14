package cn.slainc.sse.cluster;

import cn.slainc.redis.pubsub.RedisPubSubService;
import cn.slainc.sse.api.SseService;
import cn.slainc.sse.support.SseEvent;
import cn.slainc.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SseClusterTest {

    private final SseEvent event = new SseEvent("e-1", "notice", "hello", LocalDateTime.now());

    @Test
    void shouldPublishAnEnvelopeWithOriginAndUser() {
        RedisPubSubService pubSubService = mock(RedisPubSubService.class);
        RedisSseEventRelay relay = new RedisSseEventRelay(pubSubService, "modality:sse:cluster", "node-1");

        relay.publish(7L, event);

        verify(pubSubService).publishObject(eq("modality:sse:cluster"), any(SseClusterMessage.class));
        assertThat(relay.topic()).isEqualTo("modality:sse:cluster");
        assertThat(relay.nodeId()).isEqualTo("node-1");
    }

    @Test
    void shouldRoundTripTheEnvelopeThroughJson() {
        SseClusterMessage message = new SseClusterMessage("node-1", 7L, event);
        SseClusterMessage parsed = SseClusterMessage.parse(JsonUtils.toJson(message));

        assertThat(parsed).isNotNull();
        assertThat(parsed.origin()).isEqualTo("node-1");
        assertThat(parsed.userId()).isEqualTo(7L);
        assertThat(parsed.event().id()).isEqualTo("e-1");
        assertThat(parsed.event().name()).isEqualTo("notice");
        assertThat(parsed.event().data()).isEqualTo("hello");
        assertThat(SseClusterMessage.parse("not-json")).isNull();
        assertThat(SseClusterMessage.parse(null)).isNull();
        assertThat(SseClusterMessage.parse("  ")).isNull();
    }

    @Test
    void shouldSwallowPublisherFailures() {
        RedisPubSubService pubSubService = mock(RedisPubSubService.class);
        doThrow(new IllegalStateException("redis down")).when(pubSubService).publishObject(any(), any());
        RedisSseEventRelay relay = new RedisSseEventRelay(pubSubService, "modality:sse:cluster", "node-1");

        relay.publish(7L, event);
        relay.publish(null, event);
        relay.publish(7L, null);

        verify(pubSubService).publishObject(eq("modality:sse:cluster"), any(SseClusterMessage.class));
    }

    @Test
    void shouldIgnoreOwnMessagesAndDeliverForeignOnes() {
        SseService sseService = mock(SseService.class);
        RedisPubSubService pubSubService = mock(RedisPubSubService.class);
        SseClusterSubscriber subscriber = new SseClusterSubscriber(pubSubService, sseService, "modality:sse:cluster",
                "node-1");

        assertThat(subscriber.topic()).isEqualTo("modality:sse:cluster");
        subscriber.onMessage(message(new SseClusterMessage("node-1", 7L, event)), null);
        verifyNoInteractions(sseService);

        SseEvent delivered = new SseEvent("e-2", "notice", "hi", LocalDateTime.now());
        subscriber.onMessage(message(new SseClusterMessage("node-2", 7L, delivered)), null);
        verify(sseService).deliver(7L, delivered);

        subscriber.onMessage(message("unrelated"), null);
        subscriber.onMessage(null, null);
        verifyNoMoreInteractions(sseService);
    }

    @Test
    void shouldSubscribeAndUnsubscribeWithTheContainerLifecycle() throws Exception {
        RedisPubSubService pubSubService = mock(RedisPubSubService.class);
        SseClusterSubscriber subscriber = new SseClusterSubscriber(pubSubService, mock(SseService.class),
                "modality:sse:cluster", "node-1");

        subscriber.afterPropertiesSet();
        verify(pubSubService).subscribe("modality:sse:cluster", subscriber);

        subscriber.destroy();
        verify(pubSubService).unsubscribe("modality:sse:cluster");
    }

    private Message message(SseClusterMessage clusterMessage) {
        return message(JsonUtils.toJson(clusterMessage));
    }

    private Message message(String json) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
