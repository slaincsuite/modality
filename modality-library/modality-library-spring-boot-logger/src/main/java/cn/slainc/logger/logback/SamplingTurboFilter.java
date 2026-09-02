package cn.slainc.logger.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.constant.CriticalMarker;
import cn.slainc.logger.constant.LogMdcKeys;
import cn.slainc.logger.sample.LogSampler;
import cn.slainc.logger.sample.SamplingManager;
import org.slf4j.MDC;
import org.slf4j.Marker;

public class SamplingTurboFilter extends TurboFilter {

    private final LoggerProperties.Sample properties;

    private final SamplingManager samplingManager;

    private Level alwaysLogLevel;

    public SamplingTurboFilter(LoggerProperties.Sample properties, SamplingManager samplingManager) {
        this.properties = properties;
        this.samplingManager = samplingManager;
    }

    @Override
    public void start() {
        this.alwaysLogLevel = Level.toLevel(properties.getAlwaysLogLevel(), Level.WARN);
        super.start();
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params,
                              Throwable throwable) {
        if (!properties.isEnabled()) {
            return FilterReply.NEUTRAL;
        }
        Level threshold = alwaysLogLevel == null ? Level.WARN : alwaysLogLevel;
        if (level != null && level.isGreaterOrEqual(threshold)) {
            return FilterReply.NEUTRAL;
        }
        if (properties.isExemptCriticalMarker() && CriticalMarker.isCritical(marker)) {
            return FilterReply.NEUTRAL;
        }
        if (Boolean.parseBoolean(MDC.get(LogMdcKeys.SAMPLING_EXEMPT))) {
            return FilterReply.NEUTRAL;
        }
        LogSampler sampler = samplingManager == null ? null : samplingManager.sampler();
        if (sampler == null) {
            return FilterReply.NEUTRAL;
        }
        return sampler.accept(logger == null ? null : logger.getName()) ? FilterReply.NEUTRAL : FilterReply.DENY;
    }
}
