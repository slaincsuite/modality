package cn.slainc.sse.autoconfigure;

import cn.slainc.sse.MapSseConfigService;
import cn.slainc.sse.api.SseService;
import cn.slainc.sse.cluster.SseClusterSubscriber;
import cn.slainc.sse.cluster.SseEventRelay;
import cn.slainc.sse.support.SseHeartbeatScheduler;
import cn.slainc.sse.support.SseMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class SseAutoConfigurationContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SseAutoConfiguration.class));

    @Test
    void shouldWireTheDefaultBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SseService.class);
            assertThat(context).hasSingleBean(SseMetrics.class);
            assertThat(context).hasSingleBean(SseHeartbeatScheduler.class);
            assertThat(context).hasBean("sseHeartbeatScheduler");
            assertThat(context.getBean(SseService.class).connections()).isZero();
        });
    }

    @Test
    void shouldStopTheHeartbeatSchedulerWhenTheContextCloses() {
        runner.run(context -> {
            ThreadPoolTaskScheduler scheduler = context.getBean("sseHeartbeatScheduler",
                    ThreadPoolTaskScheduler.class);
            assertThat(scheduler.getScheduledExecutor()).isNotNull();
        });
    }

    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("modality.sse.enabled=false").run(context ->
                assertThat(context).doesNotHaveBean(SseService.class));
    }

    @Test
    void shouldFallBackToALocalRelayWithoutRedis() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseAutoConfiguration.class, SseClusterAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SseEventRelay.class);
                    assertThat(context.getBean(SseEventRelay.class).remote()).isFalse();
                    assertThat(context).doesNotHaveBean(SseClusterSubscriber.class);
                });
    }

    @Test
    void shouldApplyDynamicConfigurationWhenTheServiceIsPresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseDynamicAutoConfiguration.class, SseAutoConfiguration.class))
                .withBean("dynamicConfigService", MapSseConfigService.class,
                        () -> new MapSseConfigService()
                                .put(DynamicSseProperties.KEY_TIMEOUT, "PT15M")
                                .put(DynamicSseProperties.KEY_MAX_CONNECTIONS_PER_USER, "2"))
                .withPropertyValues("modality.sse.heartbeat-event=ping")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SseProperties properties = context.getBean(SseProperties.class);
                    assertThat(properties).isInstanceOf(DynamicSseProperties.class);
                    assertThat(properties.getTimeout()).isEqualTo(java.time.Duration.ofMinutes(15));
                    assertThat(properties.getMaxConnectionsPerUser()).isEqualTo(2);
                    assertThat(properties.getHeartbeatEvent()).isEqualTo("ping");
                });
    }

    @Test
    void shouldKeepTheBoundConfigurationWithoutDynamicService() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SseDynamicAutoConfiguration.class, SseAutoConfiguration.class))
                .withPropertyValues("modality.sse.timeout=PT30M")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SseProperties properties = context.getBean(SseProperties.class);
                    assertThat(properties.getTimeout()).isEqualTo(java.time.Duration.ofMinutes(30));
                });
    }
}
