package cn.slainc.monitor.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;

public class MetricNamingMeterFilter implements MeterFilter {

    private final MetricNaming naming;

    public MetricNamingMeterFilter(MetricNaming naming) {
        this.naming = naming == null ? new MetricNaming() : naming;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        String name = id.getName();
        if (name == null || !name.startsWith(naming.prefix())) {
            return id;
        }
        String canonical = naming.normalize(name);
        return canonical.equals(name) ? id : id.withName(canonical);
    }
}
