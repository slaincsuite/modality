package cn.slainc.logger.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.constant.CriticalMarker;
import cn.slainc.logger.constant.LogMdcKeys;
import cn.slainc.logger.sample.AdaptiveLogSampler;
import cn.slainc.logger.sample.SamplingManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplingTurboFilterTest {

    private static final String LOGGER_NAME = "cn.slainc.logger.sample.probe";

    private final LoggerContext context = LogbackLevelManager.currentContext();

    private TurboFilter registered;

    @AfterEach
    void tearDown() {
        MDC.clear();
        if (registered != null && context != null) {
            context.getTurboFilterList().remove(registered);
        }
    }

    private SamplingTurboFilter filter(double rate) {
        LoggerProperties properties = new LoggerProperties();
        properties.getSample().setEnabled(true);
        properties.getSample().setRate(rate);
        properties.getSample().setAlwaysLogLevel("WARN");
        SamplingManager manager = new SamplingManager(properties.getSample(), null);
        SamplingTurboFilter filter = new SamplingTurboFilter(properties.getSample(), manager);
        filter.setContext(context);
        filter.start();
        return filter;
    }

    private Logger logger() {
        Logger logger = context.getLogger(LOGGER_NAME);
        logger.setLevel(Level.TRACE);
        return logger;
    }

    @Test
    void rateZeroDeniesNonCriticalEvents() {
        SamplingTurboFilter filter = filter(0.0d);
        assertEquals(FilterReply.DENY, filter.decide(null, logger(), Level.INFO, "普通日志", null, null));
        assertEquals(FilterReply.DENY, filter.decide(null, logger(), Level.DEBUG, "调试日志", null, null));
    }

    @Test
    void rateOneAllowsEverything() {
        SamplingTurboFilter filter = filter(1.0d);
        assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger(), Level.INFO, "普通日志", null, null));
        assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger(), Level.DEBUG, "调试日志", null, null));
    }

    @Test
    void criticalLogsBypassSampling() {
        SamplingTurboFilter filter = filter(0.0d);
        assertEquals(FilterReply.NEUTRAL,
                filter.decide(CriticalMarker.INSTANCE, logger(), Level.INFO, "关键日志", null, null));
        MDC.put(LogMdcKeys.SAMPLING_EXEMPT, "true");
        assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger(), Level.INFO, "关键日志", null, null));
        MDC.remove(LogMdcKeys.SAMPLING_EXEMPT);
        assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger(), Level.ERROR, "错误日志", null, null));
        assertEquals(FilterReply.NEUTRAL, filter.decide(null, logger(), Level.WARN, "告警日志", null, null));
    }

    @Test
    void rateZeroSuppressesRealLoggerOutputButKeepsWarn() {
        LoggerContext current = context;
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(current);
        appender.start();
        Logger logger = logger();
        logger.addAppender(appender);
        registered = filter(0.0d);
        current.addTurboFilter(registered);
        try {
            logger.info("采样率 0 的 INFO 不应输出");
            logger.debug("采样率 0 的 DEBUG 不应输出");
            logger.warn("WARN 属于关键日志，不采样");
            logger.error("ERROR 属于关键日志，不采样");
            assertEquals(2, appender.list.size(), "只有 WARN/ERROR 能留下");
            assertEquals("WARN", appender.list.get(0).getLevel().toString());
            assertEquals("ERROR", appender.list.get(1).getLevel().toString());
        } finally {
            logger.detachAndStopAllAppenders();
            logger.setLevel(null);
        }
    }

    @Test
    void adaptiveSamplerLowersRateWhenTrafficExceedsTarget() throws InterruptedException {
        AdaptiveLogSampler sampler = new AdaptiveLogSampler(100.0d, 0.01d, 20L);
        for (int window = 0; window < 8; window++) {
            for (int i = 0; i < 100; i++) {
                sampler.accept(LOGGER_NAME);
            }
            Thread.sleep(25L);
        }
        assertTrue(sampler.currentRate() < 1.0d, "超过目标吞吐后自适应采样率必须下降，实际=" + sampler.currentRate());
        assertTrue(sampler.currentRate() >= 0.01d);
        assertEquals("ADAPTIVE", sampler.strategy());
    }
}
