package cn.slainc.sensitive.metrics;

import cn.slainc.sensitive.annotation.SensitiveStrategy;
import cn.slainc.sensitive.annotation.SensitiveType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerSensitiveMetrics implements SensitiveMetrics {

    public static final String MASK_COUNTER = "modality.sensitive.mask";

    public static final String SKIP_COUNTER = "modality.sensitive.skip";

    private final MeterRegistry registry;

    public MicrometerSensitiveMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordMask(SensitiveType type, SensitiveStrategy strategy) {
        Counter.builder(MASK_COUNTER)
                .tag("type", type.name())
                .tag("strategy", strategy.name())
                .register(registry)
                .increment();
    }

    @Override
    public void recordSkip() {
        Counter.builder(SKIP_COUNTER).register(registry).increment();
    }
}
