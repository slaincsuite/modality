package cn.slainc.logger.sample;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import org.springframework.beans.factory.ObjectProvider;

public class SamplingManager {

    public static final String RATE_KEY = "modality.logger.sample.rate";

    public static final String STRATEGY_KEY = "modality.logger.sample.strategy";

    private final LoggerProperties.Sample properties;

    private final ObjectProvider<DynamicConfigService> dynamicConfigService;

    private volatile LogSampler sampler;

    public SamplingManager(LoggerProperties.Sample properties,
                           ObjectProvider<DynamicConfigService> dynamicConfigService) {
        this.properties = properties;
        this.dynamicConfigService = dynamicConfigService;
        this.sampler = create();
    }

    public LogSampler sampler() {
        return sampler;
    }

    public synchronized void refresh() {
        DynamicConfigService service = dynamicConfigService == null ? null : dynamicConfigService.getIfAvailable();
        if (service != null) {
            Double rate = service.getObject(RATE_KEY, Double.class);
            if (rate != null) {
                properties.setRate(rate);
            }
            String strategy = service.getString(STRATEGY_KEY, properties.getStrategy());
            if (strategy != null && !strategy.isBlank()) {
                properties.setStrategy(strategy);
            }
        }
        this.sampler = create();
    }

    private LogSampler create() {
        if ("ADAPTIVE".equalsIgnoreCase(properties.getStrategy())) {
            return new AdaptiveLogSampler(properties.getTargetPerSecond(), properties.getMinRate(),
                    properties.getWindowMillis());
        }
        return new RateLogSampler(properties.getRate());
    }
}
