package cn.slainc.modules.audit.scheduler;

import cn.slainc.modules.audit.service.AuditAlertService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AuditAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditAlertScheduler.class);

    private final AuditAlertService alertService;

    private final long intervalSeconds;

    private final ScheduledExecutorService scheduler;

    public AuditAlertScheduler(AuditAlertService alertService, long intervalSeconds) {
        this.alertService = alertService;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "modality-audit-alert");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        long interval = Math.max(30, intervalSeconds);
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                alertService.evaluate();
            } catch (Exception e) {
                log.warn("审计告警评估执行失败: {} {}", e.getClass().getName(), e.getMessage());
            }
        }, interval, interval, TimeUnit.SECONDS);
        log.info("审计告警评估调度器已启动，周期 {} 秒", interval);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
