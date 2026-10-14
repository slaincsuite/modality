package cn.slainc.sse.autoconfigure;

import cn.slainc.redis.autoconfigure.RedisPubSubAutoConfiguration;
import cn.slainc.redis.pubsub.RedisPubSubService;
import cn.slainc.sse.api.SseService;
import cn.slainc.sse.cluster.RedisSseEventRelay;
import cn.slainc.sse.cluster.SseClusterSubscriber;
import cn.slainc.sse.cluster.SseEventRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RedisPubSubAutoConfiguration.class)
@ConditionalOnClass(RedisPubSubService.class)
@ConditionalOnProperty(prefix = SseProperties.CLUSTER_PREFIX, name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SseClusterAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SseClusterAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(SseEventRelay.class)
    public SseEventRelay sseEventRelay(ObjectProvider<RedisPubSubService> pubSubServiceProvider,
                                       SseProperties properties) {
        RedisPubSubService pubSubService = pubSubServiceProvider.getIfAvailable();
        if (pubSubService == null) {
            return SseEventRelay.local();
        }
        return new RedisSseEventRelay(pubSubService, properties.getCluster().getTopic(),
                properties.getCluster().nodeId());
    }

    @Bean
    @ConditionalOnBean(RedisPubSubService.class)
    @ConditionalOnMissingBean(SseClusterSubscriber.class)
    public SseClusterSubscriber sseClusterSubscriber(RedisPubSubService pubSubService, SseService sseService,
                                                     SseProperties properties) {
        SseClusterSubscriber subscriber = new SseClusterSubscriber(pubSubService, sseService,
                properties.getCluster().getTopic(), properties.getCluster().nodeId());
        LOGGER.info("SSE 集群事件中继已启用：topic={}，nodeId={}", properties.getCluster().getTopic(),
                properties.getCluster().nodeId());
        return subscriber;
    }
}
