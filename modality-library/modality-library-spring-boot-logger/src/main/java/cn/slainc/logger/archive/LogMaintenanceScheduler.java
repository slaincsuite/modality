package cn.slainc.logger.archive;

import cn.slainc.logger.autoconfigure.LoggerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LogMaintenanceScheduler implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(LogMaintenanceScheduler.class);

    private final LoggerProperties.Archive properties;

    private final LogArchiveService archiveService;

    private final LogCleanupService cleanupService;

    private final List<Runnable> refreshTasks;

    private ScheduledExecutorService executor;

    public LogMaintenanceScheduler(LoggerProperties.Archive properties, LogArchiveService archiveService,
                                   LogCleanupService cleanupService, List<Runnable> refreshTasks) {
        this.properties = properties;
        this.archiveService = archiveService;
        this.cleanupService = cleanupService;
        this.refreshTasks = refreshTasks == null ? List.of() : new ArrayList<>(refreshTasks);
    }

    public MaintenanceResult runOnce() {
        List<Path> archived = archiveService == null ? List.of() : archiveService.archive();
        List<Path> deleted = cleanupService == null ? List.of() : cleanupService.cleanup();
        for (Runnable task : refreshTasks) {
            try {
                task.run();
            } catch (RuntimeException ex) {
                log.warn("日志动态配置刷新失败", ex);
            }
        }
        return new MaintenanceResult(archived, deleted);
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.isEnabled() || properties.getIntervalMinutes() <= 0L) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "modality-log-maintenance");
            thread.setDaemon(true);
            return thread;
        });
        long interval = properties.getIntervalMinutes();
        executor.scheduleWithFixedDelay(this::runQuietly, interval, interval, TimeUnit.MINUTES);
        if (properties.isRunOnStartup()) {
            executor.execute(this::runQuietly);
        }
    }

    private void runQuietly() {
        try {
            runOnce();
        } catch (RuntimeException ex) {
            log.warn("日志归档清理任务执行失败", ex);
        }
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public record MaintenanceResult(List<Path> archived, List<Path> deleted) {
    }
}
