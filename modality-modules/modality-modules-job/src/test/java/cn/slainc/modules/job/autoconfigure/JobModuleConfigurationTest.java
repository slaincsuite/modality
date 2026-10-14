package cn.slainc.modules.job.autoconfigure;

import cn.slainc.job.api.JobScheduler;
import cn.slainc.modules.job.mapper.JobLogMapper;
import cn.slainc.modules.job.mapper.JobTaskMapper;
import cn.slainc.modules.job.service.JobManageService;
import cn.slainc.modules.job.scheduler.JobMaintenanceScheduler;
import cn.slainc.modules.job.store.NodeRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JobModuleConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JobModuleAutoConfiguration.class))
            .withBean(JobTaskMapper.class, () -> mock(JobTaskMapper.class))
            .withBean(JobLogMapper.class, () -> mock(JobLogMapper.class))
            .withBean(JobScheduler.class, () -> mock(JobScheduler.class))
            .withPropertyValues("modality.job.heartbeat-interval=0s",
                    "modality.job.log-purge-enabled=false");

    @Test
    void shouldCreateConsoleBeansWithoutAnyJobHandlerBean() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(JobManageService.class);
            assertThat(context).hasSingleBean(NodeRegistry.class);
            assertThat(context).hasSingleBean(JobMaintenanceScheduler.class);
            assertThat(context).hasBean("jobNodeHeartbeatScheduler");
        });
    }

    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("modality.job.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(JobManageService.class));
    }

    @Test
    void shouldBackOffWhenNoSchedulerIsAvailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JobModuleAutoConfiguration.class))
                .withBean(JobTaskMapper.class, () -> mock(JobTaskMapper.class))
                .withBean(JobLogMapper.class, () -> mock(JobLogMapper.class))
                .withPropertyValues("modality.job.heartbeat-interval=0s",
                        "modality.job.log-purge-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JobManageService.class);
                    assertThat(context).hasSingleBean(NodeRegistry.class);
                });
    }

    @Test
    void shouldLetAnApplicationReplaceTheService() {
        JobManageService custom = mock(JobManageService.class);
        runner.withBean(JobManageService.class, () -> custom)
                .run(context -> assertThat(context.getBean(JobManageService.class)).isSameAs(custom));
    }
}
