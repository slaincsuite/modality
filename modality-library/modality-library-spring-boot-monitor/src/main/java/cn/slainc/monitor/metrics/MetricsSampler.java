package cn.slainc.monitor.metrics;

import cn.slainc.monitor.config.MonitorDynamicConfig;

import java.util.concurrent.ThreadLocalRandom;

public class MetricsSampler {

    public static final String SAMPLING_RATE_KEY = "modality.monitor.metrics.sampling-rate";

    private final MonitorDynamicConfig config;

    private final double defaultRate;

    public MetricsSampler(MonitorDynamicConfig config, double defaultRate) {
        if (config == null) {
            throw new IllegalArgumentException("MonitorDynamicConfig 不能为空");
        }
        this.config = config;
        this.defaultRate = defaultRate;
    }

    public double rate() {
        return config.getDouble(SAMPLING_RATE_KEY, defaultRate);
    }

    public boolean shouldRecord() {
        double rate = rate();
        if (rate >= 1d) {
            return true;
        }
        if (rate <= 0d) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < rate;
    }
}
