package cn.slainc.session.cleanup;

import cn.slainc.session.config.SessionConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SessionCleanupScheduler implements AutoCloseable {

    public static final String THREAD_NAME = "session-cleanup";

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final SessionCleanupService cleanupService;

    private final SessionConfigReader config;

    private final ScheduledExecutorService executor;

    private volatile boolean closed;

    private volatile ScheduledFuture<?> future;

    public SessionCleanupScheduler(SessionCleanupService cleanupService, SessionConfigReader config) {
        this.cleanupService = cleanupService;
        this.config = config;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        scheduleNext();
    }

    public CleanupReport runOnce() {
        return cleanupService.cleanupAll();
    }

    public long initialDelayMillis() {
        return nextDelayMillis();
    }

    public long nextDelayMillis() {
        return Math.max(1, config.cleanupIntervalMinutes()) * 60_000L;
    }

    public boolean isRunning() {
        ScheduledFuture<?> current = future;
        return !closed && current != null && !current.isCancelled();
    }

    private void scheduleNext() {
        if (closed) {
            return;
        }
        future = executor.schedule(this::runAndReschedule, nextDelayMillis(), TimeUnit.MILLISECONDS);
    }

    private void runAndReschedule() {
        try {
            CleanupReport report = runOnce();
            if (report.total() > 0) {
                log.info("定时会话清理完成：清理残留终端 {} 个，空闲超时注销 {} 个，绝对超时注销 {} 个",
                        report.removedTerminals(), report.idleKicked(), report.lifetimeKicked());
            }
        } catch (Exception e) {
            log.warn("定时会话清理执行失败：{}", e.getMessage());
        } finally {
            scheduleNext();
        }
    }

    @Override
    public void close() {
        closed = true;
        ScheduledFuture<?> current = future;
        if (current != null) {
            current.cancel(false);
        }
        executor.shutdownNow();
    }
}
