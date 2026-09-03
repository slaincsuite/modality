package cn.slainc.logger.monitor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class MetricsAppender extends AppenderBase<ILoggingEvent> {

    private final LogMetrics metrics;

    private final LogAlertDispatcher alertDispatcher;

    public MetricsAppender(LogMetrics metrics, LogAlertDispatcher alertDispatcher) {
        this.metrics = metrics;
        this.alertDispatcher = alertDispatcher;
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            metrics.record(event.getLevel().toString(), event.getLoggerName(), event.getThrowableProxy() != null);
            if (alertDispatcher != null && event.getLevel().isGreaterOrEqual(Level.ERROR)) {
                alertDispatcher.evaluate();
            }
        } catch (RuntimeException ignored) {
            // 指标采集失败不能影响日志主链路
        }
    }
}
