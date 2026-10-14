package cn.slainc.job.autoconfigure;

import cn.slainc.job.api.JobScheduler;
import cn.slainc.job.support.JobSchedulerImpl;
import cn.slainc.job.support.QuartzBootstrap;
import cn.slainc.job.support.QuartzStoreCustomizer;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class JobAutoConfigurationTest {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(QuartzAutoConfiguration.class, JobAutoConfiguration.class))
                .withPropertyValues("spring.quartz.scheduler-name=job-config-" + UUID.randomUUID());
    }

    @Test
    void shouldCreateQuartzKernelAndDeferStartupUntilBootstrap() {
        runner().run(context -> {
            assertThat(context).hasSingleBean(Scheduler.class);
            assertThat(context).hasSingleBean(JobScheduler.class);
            assertThat(context).hasSingleBean(JobSchedulerImpl.class);
            assertThat(context).hasSingleBean(QuartzStoreCustomizer.class);
            assertThat(context).hasSingleBean(QuartzBootstrap.class);

            QuartzStoreCustomizer customizer = context.getBean(QuartzStoreCustomizer.class);
            assertThat(customizer.isJdbcStore()).as("没有 DataSource 时退回 RAMJobStore").isFalse();
            assertThat(customizer.isStartupDeferred()).isTrue();

            Scheduler scheduler = context.getBean(Scheduler.class);
            assertThat(scheduler.isStarted()).as("任务定义同步完之前不启动调度线程").isFalse();

            context.getBean(QuartzBootstrap.class).start();

            assertThat(scheduler.isStarted()).isTrue();
            assertThat(scheduler.getMetaData().getJobStoreClass().getName()).contains("RAMJobStore");
        });
    }

    @Test
    void shouldShutdownQuartzSchedulerWithTheSpringContext() throws Exception {
        Scheduler[] holder = new Scheduler[1];
        runner().run(context -> holder[0] = context.getBean(Scheduler.class));

        assertThat(holder[0].isShutdown()).isTrue();
    }

    @Test
    void shouldUseJdbcJobStoreWhenDataSourceIsPresent() {
        runner().withBean(DataSource.class, () -> mock(DataSource.class)).run(context -> {
            assertThat(context.getBean(QuartzStoreCustomizer.class).isJdbcStore()).isTrue();
            assertThat(context.getBean(Scheduler.class).isStarted()).isFalse();
        });
    }

    @Test
    void shouldHonorRamJobStoreOverride() {
        runner().withBean(DataSource.class, () -> mock(DataSource.class))
                .withPropertyValues("modality.job.store=memory")
                .run(context -> assertThat(context.getBean(QuartzStoreCustomizer.class).isJdbcStore()).isFalse());
    }

    @Test
    void shouldFailFastWhenJdbcJobStoreIsRequiredWithoutDataSource() {
        runner().withPropertyValues("modality.job.store=jdbc")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldBackOffWhenJobsAreDisabled() {
        runner().withPropertyValues("modality.job.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(JobScheduler.class);
            assertThat(context).hasSingleBean(Scheduler.class);
        });
    }
}
