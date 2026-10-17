package cn.slainc.services.livemonitor.autoconfigure;

import cn.slainc.services.livemonitor.mapper.LiveStreamMapper;
import cn.slainc.services.livemonitor.service.stream.LiveStreamProbe;
import cn.slainc.services.livemonitor.service.stream.LiveStreamService;
import cn.slainc.services.livemonitor.support.StreamUrlGuard;
import cn.slainc.services.livemonitor.web.stream.LiveStreamController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LiveMonitorAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LiveMonitorAutoConfiguration.class))
            .withBean(LiveStreamMapper.class, () -> mock(LiveStreamMapper.class));

    @Test
    void shouldCreateCompleteBeanChain() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(StreamUrlGuard.class);
            assertThat(context).hasSingleBean(LiveStreamProbe.class);
            assertThat(context).hasSingleBean(LiveStreamService.class);
            assertThat(context).hasSingleBean(LiveStreamController.class);
        });
    }

    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("modality.live-monitor.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(StreamUrlGuard.class);
                    assertThat(context).doesNotHaveBean(LiveStreamService.class);
                    assertThat(context).doesNotHaveBean(LiveStreamController.class);
                });
    }

    @Test
    void shouldAllowApplicationOverride() {
        StreamUrlGuard custom = new StreamUrlGuard();
        runner.withBean(StreamUrlGuard.class, () -> custom)
                .run(context -> assertThat(context.getBean(StreamUrlGuard.class)).isSameAs(custom));
    }
}
