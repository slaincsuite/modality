package cn.slainc.modules.job.scheduler;

import cn.slainc.modules.job.store.NodeRegistry;

import cn.slainc.modules.job.autoconfigure.JobModuleProperties;
import cn.slainc.modules.job.service.JobManageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class JobMaintenanceSchedulerTest {

    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

    private JobMaintenanceScheduler maintenance;

    @AfterEach
    void tearDown() {
        if (maintenance != null) {
            maintenance.destroy();
        }
        taskScheduler.shutdown();
    }

    @Test
    void shouldHeartbeatOnStartupAndPurgeLogsPeriodically() {
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("test-maintenance-");
        taskScheduler.initialize();
        NodeRegistry nodeRegistry = mock(NodeRegistry.class);
        JobManageService jobManageService = mock(JobManageService.class);
        JobModuleProperties properties = new JobModuleProperties();
        properties.setHeartbeatInterval(Duration.ofMillis(50));
        properties.setLogPurgeInterval(Duration.ofMillis(50));
        maintenance = new JobMaintenanceScheduler(taskScheduler, nodeRegistry, jobManageService, properties);

        maintenance.afterPropertiesSet();

        verify(nodeRegistry, timeout(2000L).atLeastOnce()).heartbeat();
        verify(jobManageService, timeout(2000L).atLeastOnce()).purgeExpiredLogs();
    }

    @Test
    void shouldLogHeartbeatFailuresInsteadOfFailingTheSchedule() {
        taskScheduler.setPoolSize(1);
        taskScheduler.initialize();
        NodeRegistry nodeRegistry = mock(NodeRegistry.class);
        doThrow(new IllegalStateException("redis 不可用")).when(nodeRegistry).heartbeat();
        JobModuleProperties properties = new JobModuleProperties();
        maintenance = new JobMaintenanceScheduler(taskScheduler, nodeRegistry, mock(JobManageService.class),
                properties);

        assertThatCode(maintenance::afterPropertiesSet).doesNotThrowAnyException();
    }
}
