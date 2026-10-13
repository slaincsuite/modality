package cn.slainc.logger.monitor;

import cn.slainc.logger.autoconfigure.LoggerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class LogAlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LogAlertDispatcher.class);

    public static final String TYPE_ERROR_RATE = "ERROR_RATE";

    public static final String TYPE_ERROR_COUNT = "ERROR_COUNT";

    private final LoggerProperties.Monitor properties;

    private final LogMetrics metrics;

    private final List<LogAlertHook> hooks;

    private final AtomicLong lastAlertAt = new AtomicLong();

    public LogAlertDispatcher(LoggerProperties.Monitor properties, LogMetrics metrics, List<LogAlertHook> hooks) {
        this.properties = properties;
        this.metrics = metrics;
        List<LogAlertHook> sorted = hooks == null ? new ArrayList<>() : new ArrayList<>(hooks);
        sorted.sort((left, right) -> Integer.compare(left.order(), right.order()));
        this.hooks = sorted;
    }

    public List<LogAlert> evaluate() {
        if (!properties.isEnabled()) {
            return List.of();
        }
        LogMetricsSnapshot snapshot = metrics.windowSnapshot(Math.max(properties.getWindowSeconds(), 1L) * 1000L);
        List<LogAlert> alerts = new ArrayList<>(2);
        if (snapshot.total() >= properties.getMinSampleCount()
                && snapshot.errorRate() >= properties.getErrorRateThreshold()) {
            alerts.add(new LogAlert(TYPE_ERROR_RATE,
                    "日志错误率超过阈值：" + format(snapshot.errorRate()) + " >= "
                            + format(properties.getErrorRateThreshold()),
                    snapshot.errorRate(), snapshot.errorCount(), snapshot.total(),
                    properties.getWindowSeconds(), Instant.now(), null));
        }
        if (snapshot.errorCount() >= properties.getErrorCountThreshold()) {
            alerts.add(new LogAlert(TYPE_ERROR_COUNT,
                    "日志错误条数超过阈值：" + snapshot.errorCount() + " >= " + properties.getErrorCountThreshold(),
                    snapshot.errorRate(), snapshot.errorCount(), snapshot.total(),
                    properties.getWindowSeconds(), Instant.now(), null));
        }
        if (alerts.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        long previous = lastAlertAt.get();
        if (previous != 0L && now - previous < properties.getAlertIntervalSeconds() * 1000L) {
            return List.of();
        }
        if (!lastAlertAt.compareAndSet(previous, now)) {
            return List.of();
        }
        for (LogAlert alert : alerts) {
            for (LogAlertHook hook : hooks) {
                try {
                    hook.onAlert(alert);
                } catch (RuntimeException ex) {
                    log.warn("日志告警钩子执行失败：{}", hook.getClass().getName(), ex);
                }
            }
        }
        return alerts;
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
