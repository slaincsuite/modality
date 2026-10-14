package cn.slainc.sse.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

public class SseHeartbeatScheduler implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(SseHeartbeatScheduler.class);

    private final ThreadPoolTaskScheduler scheduler;

    private final Runnable heartbeatTask;

    private final Duration interval;

    private volatile ScheduledFuture<?> future;

    public SseHeartbeatScheduler(ThreadPoolTaskScheduler scheduler, Runnable heartbeatTask, Duration interval) {
        this.scheduler = scheduler;
        this.heartbeatTask = heartbeatTask;
        this.interval = interval;
    }

    @Override
    public void start() {
        if (future != null || interval == null || interval.isZero() || interval.isNegative()) {
            return;
        }
        try {
            future = scheduler.scheduleAtFixedRate(this::heartbeat, interval);
        } catch (RuntimeException e) {
            LOGGER.warn("调度 SSE 心跳失败：interval={}，原因={}", interval, e.getMessage());
        }
    }

    private void heartbeat() {
        try {
            heartbeatTask.run();
        } catch (RuntimeException e) {
            LOGGER.warn("SSE 心跳执行失败：原因={}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        ScheduledFuture<?> current = future;
        future = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    @Override
    public boolean isRunning() {
        ScheduledFuture<?> current = future;
        return current != null && !current.isCancelled();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
