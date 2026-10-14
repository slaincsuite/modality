package cn.slainc.sse.autoconfigure;

import cn.slainc.redis.pubsub.RedisPubSubService;
import cn.slainc.sse.api.SseService;
import cn.slainc.sse.cluster.RedisSseEventRelay;
import cn.slainc.sse.cluster.SseClusterSubscriber;
import cn.slainc.sse.cluster.SseEventRelay;
import cn.slainc.sse.support.SseEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SseClusterAutoConfigurationTest {

    private final SseClusterAutoConfiguration configuration = new SseClusterAutoConfiguration();

    private final SseProperties properties = new SseProperties();

    @Test
    void shouldPublishThroughRedisWhenThePubSubServiceIsAvailable() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RedisPubSubService pubSubService = mock(RedisPubSubService.class);
        beanFactory.registerSingleton("redisPubSubService", pubSubService);

        SseEventRelay relay = configuration.sseEventRelay(beanFactory.getBeanProvider(RedisPubSubService.class),
                properties);

        assertThat(relay).isInstanceOf(RedisSseEventRelay.class);
        assertThat(relay.remote()).isTrue();

        SseEvent event = new SseEvent("e-1", "notice", "hello", LocalDateTime.now());
        relay.publish(1L, event);
        verify(pubSubService).publishObject(eq(properties.getCluster().getTopic()), any());

        assertThat(((RedisSseEventRelay) relay).nodeId()).isEqualTo(properties.getCluster().nodeId());
    }

    @Test
    void shouldFallBackToALocalRelayWithoutRedis() {
        SseEventRelay relay = configuration.sseEventRelay(
                new DefaultListableBeanFactory().getBeanProvider(RedisPubSubService.class), properties);

        assertThat(relay.remote()).isFalse();
        relay.publish(1L, new SseEvent("e-1", "notice", "hello", LocalDateTime.now()));
    }

    @Test
    void shouldGenerateAStableNodeId() {
        SseProperties.Cluster cluster = new SseProperties.Cluster();
        String first = cluster.nodeId();

        assertThat(first).isNotBlank();
        assertThat(cluster.nodeId()).isEqualTo(first);
        assertThat(new SseProperties.Cluster().nodeId()).isNotEqualTo(first);
    }

    @Test
    void shouldBuildTheSubscriberForTheConfiguredTopic() throws Exception {
        properties.getCluster().setTopic("custom:topic");
        RedisPubSubService pubSubService = mock(RedisPubSubService.class);

        SseClusterSubscriber subscriber = configuration.sseClusterSubscriber(pubSubService,
                mock(SseService.class), properties);

        assertThat(subscriber.topic()).isEqualTo("custom:topic");
        subscriber.afterPropertiesSet();
        verify(pubSubService).subscribe(eq("custom:topic"), any(SseClusterSubscriber.class));
        subscriber.destroy();
        verify(pubSubService).unsubscribe("custom:topic");
    }
}
