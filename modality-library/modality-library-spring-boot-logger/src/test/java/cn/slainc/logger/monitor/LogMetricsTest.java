package cn.slainc.logger.monitor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.logback.LogbackLevelManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMetricsTest {

    private Logger logger;

    @AfterEach
    void tearDown() {
        if (logger != null) {
            logger.detachAndStopAllAppenders();
            logger.setLevel(null);
            logger = null;
        }
    }

    @Test
    void countsLevelsAndComputesErrorRate() {
        InMemoryLogMetrics metrics = new InMemoryLogMetrics();
        metrics.record("INFO", "a", false);
        metrics.record("INFO", "a", false);
        metrics.record("WARN", "a", false);
        metrics.record("ERROR", "a", true);

        LogMetricsSnapshot snapshot = metrics.snapshot();
        assertEquals(4L, snapshot.total());
        assertEquals(2L, snapshot.levelCounts().get("INFO"));
        assertEquals(1L, snapshot.warnCount());
        assertEquals(1L, snapshot.errorCount());
        assertEquals(1L, snapshot.withExceptionCount());
        assertEquals(0.25d, snapshot.errorRate(), 0.0001d);
    }

    @Test
    void firesAlertHookWhenErrorRateExceedsThreshold() {
        LoggerProperties.Monitor monitor = new LoggerProperties().getMonitor();
        monitor.setMinSampleCount(2L);
        monitor.setErrorRateThreshold(0.5d);
        monitor.setErrorCountThreshold(100L);
        monitor.setAlertIntervalSeconds(0L);
        InMemoryLogMetrics metrics = new InMemoryLogMetrics();
        List<LogAlert> alerts = new ArrayList<>();
        LogAlertDispatcher dispatcher = new LogAlertDispatcher(monitor, metrics, List.of(new LogAlertHook() {
            @Override
            public void onAlert(LogAlert alert) {
                alerts.add(alert);
            }
        }));

        metrics.record("INFO", "a", false);
        metrics.record("ERROR", "a", true);
        List<LogAlert> fired = dispatcher.evaluate();

        assertEquals(1, fired.size());
        assertEquals(LogAlertDispatcher.TYPE_ERROR_RATE, fired.get(0).type());
        assertEquals(1, alerts.size(), "告警钩子必须被回调");
        assertTrue(alerts.get(0).message().contains("日志错误率超过阈值"));
    }

    @Test
    void metricsAppenderFeedsFromRealLogbackEvents() {
        LoggerProperties properties = new LoggerProperties();
        InMemoryLogMetrics metrics = new InMemoryLogMetrics();
        List<LogAlert> alerts = new ArrayList<>();
        properties.getMonitor().setMinSampleCount(1L);
        properties.getMonitor().setErrorRateThreshold(0.3d);
        properties.getMonitor().setErrorCountThreshold(100L);
        properties.getMonitor().setAlertIntervalSeconds(0L);
        LogAlertDispatcher dispatcher = new LogAlertDispatcher(properties.getMonitor(), metrics,
                List.of(alerts::add));
        LoggerContext context = LogbackLevelManager.currentContext();
        MetricsAppender appender = new MetricsAppender(metrics, dispatcher);
        appender.setContext(context);
        appender.setName("testMetrics");
        appender.start();
        logger = context.getLogger("cn.slainc.logger.metrics.probe");
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);

        logger.info("普通日志");
        logger.info("普通日志");
        logger.error("失败日志", new IllegalStateException("爆炸"));

        LogMetricsSnapshot snapshot = metrics.snapshot();
        assertEquals(3L, snapshot.total());
        assertEquals(1L, snapshot.errorCount());
        assertEquals(1L, snapshot.withExceptionCount());
        assertEquals(1, alerts.size(), "ERROR 必须触发告警评估");
    }

    @Test
    void micrometerBinderRegistersCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerLogMetrics metrics = new MicrometerLogMetrics(registry);
        metrics.record("INFO", "a", false);
        metrics.record("ERROR", "a", true);

        assertEquals(1.0d, registry.get("modality.logger.events").tag("level", "INFO").counter().count());
        assertEquals(1.0d, registry.get("modality.logger.events").tag("level", "ERROR").counter().count());
        assertEquals(1.0d, registry.get("modality.logger.events").tag("exception", "true").counter().count());
        assertEquals(2L, metrics.snapshot().total());
    }
}
