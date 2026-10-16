package cn.slainc.changeaudit.autoconfigure;

import cn.slainc.changeaudit.dao.JdbcChangeRecorder;
import cn.slainc.changeaudit.metrics.ChangeAuditMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.function.ToLongFunction;

public class ChangeAuditMicrometerBinder {

    private static final String PREFIX = "modality.change-audit.";

    private final JdbcChangeRecorder recorder;

    private final MeterRegistry registry;

    public ChangeAuditMicrometerBinder(JdbcChangeRecorder recorder, MeterRegistry registry) {
        this.recorder = recorder;
        this.registry = registry;
        bind("record.success", ChangeAuditMetrics::recorded);
        bind("record.failure", ChangeAuditMetrics::failed);
    }

    private void bind(String name, ToLongFunction<ChangeAuditMetrics> extractor) {
        Gauge.builder(PREFIX + name, recorder, current -> (double) extractor.applyAsLong(current.metrics()))
                .register(registry);
    }
}
