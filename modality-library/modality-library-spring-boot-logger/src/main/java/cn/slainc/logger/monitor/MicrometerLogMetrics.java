package cn.slainc.logger.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MicrometerLogMetrics extends InMemoryLogMetrics {

    private final MeterRegistry registry;

    private final Map<String, Counter> levelCounters = new ConcurrentHashMap<>();

    private final Counter exceptionCounter;

    public MicrometerLogMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.exceptionCounter = Counter.builder("modality.logger.events").tag("exception", "true").register(registry);
    }

    @Override
    public void record(String level, String loggerName, boolean hasException) {
        super.record(level, loggerName, hasException);
        String normalized = level == null ? "UNKNOWN" : level.toUpperCase(java.util.Locale.ROOT);
        levelCounters.computeIfAbsent(normalized,
                key -> Counter.builder("modality.logger.events").tag("level", key).register(registry)).increment();
        if (hasException) {
            exceptionCounter.increment();
        }
    }

    public MeterRegistry registry() {
        return registry;
    }
}
