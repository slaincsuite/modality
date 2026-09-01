package cn.slainc.storage.autoconfigure;

import cn.slainc.storage.lifecycle.LifecycleReport;
import cn.slainc.storage.lifecycle.StorageLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StorageLifecycleScheduler implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(StorageLifecycleScheduler.class);

    private final StorageLifecycleService lifecycleService;

    private final Duration fixedDelay;

    private final ScheduledExecutorService executor;

    public StorageLifecycleScheduler(StorageLifecycleService lifecycleService, Duration fixedDelay) {
        this.lifecycleService = lifecycleService;
        this.fixedDelay = fixedDelay == null || fixedDelay.isNegative() || fixedDelay.isZero()
                ? Duration.ofHours(6) : fixedDelay;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "storage-lifecycle");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void afterPropertiesSet() {
        long delay = Math.max(1, fixedDelay.toMillis());
        executor.scheduleWithFixedDelay(this::runSafely, delay, delay, TimeUnit.MILLISECONDS);
        log.info("对象存储生命周期定时任务已启动，执行间隔 {} 秒", delay / 1000);
    }

    public LifecycleReport runOnce() {
        return lifecycleService.run();
    }

    private void runSafely() {
        try {
            LifecycleReport report = lifecycleService.run();
            log.info("对象存储生命周期任务完成：扫描 {}，过期清理 {}，归档 {}，失败 {}，回收 {} 字节", report.scanned(),
                    report.expired(), report.archived(), report.failed(), report.reclaimedBytes());
        } catch (RuntimeException ex) {
            log.warn("对象存储生命周期任务执行失败：{}", ex.getMessage());
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
