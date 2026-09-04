package cn.slainc.monitor.alert;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompositeAlertMetricSource implements AlertMetricSource {

    private final List<AlertMetricSource> sources;

    public CompositeAlertMetricSource(List<AlertMetricSource> sources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    @Override
    public Double value(String metric) {
        for (AlertMetricSource source : sources) {
            Double value = source.value(metric);
            if (value != null && !value.isNaN()) {
                return value;
            }
        }
        return null;
    }

    @Override
    public Set<String> metrics() {
        Set<String> names = new LinkedHashSet<>();
        for (AlertMetricSource source : sources) {
            names.addAll(source.metrics());
        }
        return names;
    }

    public Map<String, Double> snapshot(Set<String> names) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String name : names) {
            Double value = value(name);
            if (value != null) {
                values.put(name, value);
            }
        }
        return values;
    }
}
