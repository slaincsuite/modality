package cn.slainc.monitor.alert;

import cn.slainc.monitor.autoconfigure.MonitorProperties;
import cn.slainc.monitor.config.MonitorDynamicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AlertEvaluationScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationScheduler.class);

    public static final String INTERVAL_KEY = "modality.monitor.alert.evaluation-interval-millis";

    private final AlertService alertService;

    private final MonitorDynamicConfig config;

    private final MonitorProperties properties;

    private volatile ScheduledExecutorService executor;

    private volatile boolean running;

    private volatile long lastEvaluation;

    public AlertEvaluationScheduler(AlertService alertService, MonitorDynamicConfig config,
                                    MonitorProperties properties) {
        this.alertService = alertService;
        this.config = config;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.getAlert().isEnabled()) {
            log.info("告警评估调度未启动：modality.monitor.alert.enabled=false");
            return;
        }
        running = true;
        // 上下文 pause/restart 会 stop() 之后再 start()，这里必须能重建已被关闭的线程池
        ScheduledExecutorService current = executor;
        if (current == null || current.isShutdown()) {
            current = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "alert-evaluator");
                thread.setDaemon(true);
                return thread;
            });
            executor = current;
        }
        current.scheduleWithFixedDelay(this::tick, 1000L, 1000L, TimeUnit.MILLISECONDS);
        log.info("告警评估调度已启动，基础扫描间隔 1 秒");
    }

    @Override
    public void stop() {
        running = false;
        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE + 100;
    }

    public void evaluateNow() {
        alertService.evaluateOnce();
    }

    private void tick() {
        try {
            long interval = Math.max(config.getLong(INTERVAL_KEY,
                    properties.getAlert().getEvaluationIntervalMillis()), 1000L);
            long now = System.currentTimeMillis();
            if (now - lastEvaluation < interval) {
                return;
            }
            lastEvaluation = now;
            alertService.evaluateOnce(now);
        } catch (RuntimeException exception) {
            log.warn("告警评估失败: {}", exception.getMessage());
        }
    }
}
