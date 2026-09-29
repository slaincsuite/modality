package cn.slainc.modules.audit.scheduler;

import cn.slainc.modules.audit.service.AuditCleanupService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// 审计数据清理调度器：daemon 单线程，首次延迟一个周期后执行，周期与保留天数均支持热配置
public class AuditCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditCleanupScheduler.class);

    private final AuditCleanupService cleanupService;

    private final long intervalSeconds;

    private final ScheduledExecutorService scheduler;

    public AuditCleanupScheduler(AuditCleanupService cleanupService, long intervalSeconds) {
        this.cleanupService = cleanupService;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "modality-audit-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        long interval = Math.max(60, intervalSeconds);
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                cleanupService.cleanup();
            } catch (Exception e) {
                log.warn("审计数据定时清理执行失败: {} {}", e.getClass().getName(), e.getMessage());
            }
        }, interval, interval, TimeUnit.SECONDS);
        log.info("审计数据清理调度器已启动，周期 {} 秒", interval);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
