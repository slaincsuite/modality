package cn.slainc.modules.job.support;

import cn.slainc.modules.job.autoconfigure.JobModuleProperties;
import cn.slainc.modules.job.service.JobManageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

// 维护调度器：节点心跳与日志清理两条固定频率任务，异常只告警不中断
public class JobMaintenanceScheduler implements InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobMaintenanceScheduler.class);

    private final ThreadPoolTaskScheduler taskScheduler;

    private final NodeRegistry nodeRegistry;

    private final JobManageService jobManageService;

    private final JobModuleProperties properties;

    private final List<ScheduledFuture<?>> futures = new ArrayList<>();

    public JobMaintenanceScheduler(ThreadPoolTaskScheduler taskScheduler, NodeRegistry nodeRegistry,
                                   JobManageService jobManageService, JobModuleProperties properties) {
        this.taskScheduler = taskScheduler;
        this.nodeRegistry = nodeRegistry;
        this.jobManageService = jobManageService;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        Duration interval = properties.getHeartbeatInterval();
        if (isPositive(interval)) {
            heartbeat();
            futures.add(taskScheduler.scheduleAtFixedRate(this::heartbeat, interval));
        } else {
            LOGGER.warn("节点心跳已停用：heartbeatInterval={}", interval);
        }
        Duration purgeInterval = properties.getLogPurgeInterval();
        if (properties.isLogPurgeEnabled() && isPositive(purgeInterval)) {
            futures.add(taskScheduler.scheduleAtFixedRate(this::purgeLogs, purgeInterval));
        }
    }

    void heartbeat() {
        try {
            nodeRegistry.heartbeat();
        } catch (RuntimeException e) {
            LOGGER.warn("节点心跳失败 nodeId={}", nodeRegistry.nodeId(), e);
        }
    }

    void purgeLogs() {
        try {
            jobManageService.purgeExpiredLogs();
        } catch (RuntimeException e) {
            LOGGER.error("任务日志清理失败", e);
        }
    }

    @Override
    public void destroy() {
        futures.forEach(future -> future.cancel(false));
        futures.clear();
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
