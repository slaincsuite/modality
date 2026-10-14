package cn.slainc.modules.auth.scheduler;
import cn.slainc.modules.auth.service.closure.AccountClosureService;

import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.utils.thread.ThreadPoolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AccountClosureScheduler implements AutoCloseable {

    public static final String THREAD_NAME = "auth-closure";

    private static final Logger log = LoggerFactory.getLogger(AccountClosureScheduler.class);

    private final AccountClosureService closureService;

    private final AuthProperties properties;

    private final ScheduledExecutorService executor;

    private volatile boolean closed;

    private volatile ScheduledFuture<?> future;

    public AccountClosureScheduler(AccountClosureService closureService, AuthProperties properties) {
        this.closureService = closureService;
        this.properties = properties;
        this.executor = ThreadPoolUtils.newDaemonSingleThreadScheduler(THREAD_NAME);
        scheduleNext();
    }

    public int runOnce() {
        return closureService.executeDue();
    }

    public boolean isRunning() {
        return !closed && future != null && !future.isCancelled();
    }

    private void scheduleNext() {
        if (closed) {
            return;
        }
        future = executor.schedule(this::runAndReschedule,
                Math.max(1, properties.getClosure().getCleanupIntervalSeconds()), TimeUnit.SECONDS);
    }

    private void runAndReschedule() {
        try {
            int executed = runOnce();
            if (executed > 0) {
                log.info("定时账号注销执行完成：本轮执行 {} 笔", executed);
            }
        } catch (Exception e) {
            log.warn("定时账号注销执行失败：{}", e.getMessage());
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
