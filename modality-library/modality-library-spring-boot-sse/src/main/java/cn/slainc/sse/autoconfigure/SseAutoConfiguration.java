package cn.slainc.sse.autoconfigure;

import cn.slainc.sse.spi.SseService;
import cn.slainc.sse.support.SseServiceImpl;
import cn.slainc.sse.handshake.SseAuthenticator;
import cn.slainc.sse.handshake.TokenSseAuthenticator;
import cn.slainc.sse.cluster.SseEventRelay;
import cn.slainc.sse.support.SseConnectionRegistry;
import cn.slainc.sse.support.SseEventStore;
import cn.slainc.sse.support.SseHeartbeatScheduler;
import cn.slainc.sse.support.SseMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@AutoConfiguration
@EnableConfigurationProperties(SseProperties.class)
@ConditionalOnProperty(prefix = SseProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class SseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SseConnectionRegistry.class)
    public SseConnectionRegistry sseConnectionRegistry() {
        return new SseConnectionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(SseEventStore.class)
    public SseEventStore sseEventStore(SseProperties properties) {
        return new SseEventStore(properties.getReplayBufferSize());
    }

    @Bean
    @ConditionalOnMissingBean(SseMetrics.class)
    public SseMetrics sseMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new SseMetrics(meterRegistryProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(SseAuthenticator.class)
    public TokenSseAuthenticator tokenSseAuthenticator(SseProperties properties) {
        return new TokenSseAuthenticator(properties);
    }

    @Bean
    @ConditionalOnMissingBean(SseService.class)
    public SseService sseService(SseConnectionRegistry registry, SseEventStore eventStore, SseProperties properties,
                                 SseMetrics metrics, ObjectProvider<SseEventRelay> eventRelayProvider,
                                 ObjectProvider<SseAuthenticator> authenticatorProvider) {
        return new SseServiceImpl(registry, eventStore, properties, metrics,
                eventRelayProvider.getIfAvailable(SseEventRelay::local), authenticatorProvider.getIfAvailable());
    }

    @Bean(name = "sseHeartbeatScheduler", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "sseHeartbeatScheduler")
    public ThreadPoolTaskScheduler sseHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("modality-sse-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean(SseHeartbeatScheduler.class)
    public SseHeartbeatScheduler sseHeartbeatRunner(SseService sseService, SseProperties properties,
                                                    @Qualifier("sseHeartbeatScheduler")
                                                    ThreadPoolTaskScheduler heartbeatScheduler) {
        return new SseHeartbeatScheduler(heartbeatScheduler, sseService::heartbeat,
                properties.getHeartbeatInterval());
    }
}
