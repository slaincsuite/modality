package cn.slainc.monitor.alert;

import java.util.Set;

public interface AlertMetricSource {

    Double value(String metric);

    default Set<String> metrics() {
        return Set.of();
    }
}
