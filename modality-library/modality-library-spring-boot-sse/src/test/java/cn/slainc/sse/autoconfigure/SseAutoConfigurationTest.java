package cn.slainc.sse.autoconfigure;

import cn.slainc.sse.spi.SseService;
import cn.slainc.sse.handshake.SseAuthenticator;
import cn.slainc.sse.handshake.TokenSseAuthenticator;
import cn.slainc.sse.cluster.SseEventRelay;
import cn.slainc.sse.support.SseConnectionRegistry;
import cn.slainc.sse.support.SseEventStore;
import cn.slainc.sse.support.SseHeartbeatScheduler;
import cn.slainc.sse.support.SseMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseAutoConfigurationTest {

    private final SseAutoConfiguration configuration = new SseAutoConfiguration();

    private final SseProperties properties = new SseProperties();

    @Test
    void shouldExposeTheMetricsToMicrometer() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SseMetrics metrics = configuration.sseMetrics(new SimpleMeterRegistryProvider(meterRegistry));

        metrics.connectionOpened();
        metrics.connectionRejected();
        metrics.eventSent(3);
        metrics.heartbeat(2);

        assertThat(meterRegistry.get(SseMetrics.METRIC_CONNECTIONS).tag("result", "opened")
                .functionCounter().count()).isEqualTo(1D);
        assertThat(meterRegistry.get(SseMetrics.METRIC_CONNECTIONS).tag("result", "rejected")
                .functionCounter().count()).isEqualTo(1D);
        assertThat(meterRegistry.get(SseMetrics.METRIC_EVENTS).tag("type", "sent").functionCounter().count())
                .isEqualTo(3D);
        assertThat(meterRegistry.get(SseMetrics.METRIC_HEARTBEATS).functionCounter().count()).isEqualTo(2D);
        assertThat(metrics.snapshot()).containsEntry("eventsSent", 3L);
    }

    @Test
    void shouldProvideATokenAuthenticatorByDefault() {
        SseAuthenticator authenticator = configuration.tokenSseAuthenticator(properties);

        assertThat(authenticator).isInstanceOf(TokenSseAuthenticator.class);
    }

    @Test
    void shouldNotInitialiseTheHeartbeatSchedulerManually() {
        ThreadPoolTaskScheduler scheduler = configuration.sseHeartbeatScheduler();

        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("modality-sse-heartbeat-");
        assertThatThrownBy(scheduler::getScheduledExecutor).isInstanceOf(IllegalStateException.class);

        scheduler.afterPropertiesSet();
        assertThat(scheduler.getScheduledExecutor()).isNotNull();
        scheduler.shutdown();
    }

    @Test
    void shouldBuildTheServiceWithALocalRelayWhenRedisIsAbsent() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        SseConnectionRegistry registry = configuration.sseConnectionRegistry();
        SseEventStore eventStore = configuration.sseEventStore(properties);
        SseMetrics metrics = configuration.sseMetrics(
                new SimpleMeterRegistryProvider(new SimpleMeterRegistry()));
        SseAuthenticator authenticator = configuration.tokenSseAuthenticator(properties);

        SseService service = configuration.sseService(registry, eventStore, properties, metrics,
                beanFactory.getBeanProvider(SseEventRelay.class),
                beanFactory.getBeanProvider(SseAuthenticator.class));

        assertThat(service).isNotNull();
        assertThat(service.send(1L, "notice", "nobody")).isZero();
    }

    @Test
    void shouldInjectTheResolvedAuthenticatorIntoTheService() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("sseAuthenticator",
                (SseAuthenticator) handshake -> new cn.slainc.sse.handshake.SseUser(7L, "u7"));

        SseService service = configuration.sseService(configuration.sseConnectionRegistry(),
                configuration.sseEventStore(properties), properties,
                configuration.sseMetrics(new SimpleMeterRegistryProvider(new SimpleMeterRegistry())),
                beanFactory.getBeanProvider(SseEventRelay.class),
                beanFactory.getBeanProvider(SseAuthenticator.class));

        // 请求不携带令牌，默认令牌认证器会拒绝，只有注入的自定义认证器能让订阅成功
        service.subscribe(new cn.slainc.sse.handshake.SseHandshake(
                new org.springframework.mock.web.MockHttpServletRequest()));
        assertThat(service.connections(7L)).isEqualTo(1);
    }

    @Test
    void shouldExposeTheHeartbeatRunner() {
        SseConnectionRegistry registry = configuration.sseConnectionRegistry();
        SseEventStore eventStore = configuration.sseEventStore(properties);
        SseService service = configuration.sseService(registry, eventStore, properties,
                configuration.sseMetrics(new SimpleMeterRegistryProvider(new SimpleMeterRegistry())),
                new DefaultListableBeanFactory().getBeanProvider(SseEventRelay.class),
                new DefaultListableBeanFactory().getBeanProvider(SseAuthenticator.class));
        ThreadPoolTaskScheduler scheduler = configuration.sseHeartbeatScheduler();
        scheduler.afterPropertiesSet();

        SseHeartbeatScheduler runner = configuration.sseHeartbeatRunner(service, properties, scheduler);
        assertThat(runner).isNotNull();
        assertThat(runner.isRunning()).isFalse();
        runner.stop();

        scheduler.shutdown();
    }

    private static final class SimpleMeterRegistryProvider
            implements org.springframework.beans.factory.ObjectProvider<MeterRegistry> {

        private final MeterRegistry registry;

        private SimpleMeterRegistryProvider(MeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        public MeterRegistry getObject() {
            return registry;
        }

        @Override
        public MeterRegistry getObject(Object... args) {
            return registry;
        }

        @Override
        public MeterRegistry getIfAvailable() {
            return registry;
        }

        @Override
        public MeterRegistry getIfUnique() {
            return registry;
        }
    }
}
