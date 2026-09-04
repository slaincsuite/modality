package cn.slainc.monitor.alert;

import cn.slainc.monitor.metrics.BusinessMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MeterRegistryAlertMetricSource implements AlertMetricSource {

    public static final String HTTP_ERROR_RATE = "http.request.error.rate";

    public static final String HTTP_ERROR_RATE_4XX = "http.request.error.rate.4xx";

    public static final String HTTP_ERROR_RATE_5XX = "http.request.error.rate.5xx";

    public static final String JVM_HEAP_USAGE = "jvm.heap.usage";

    public static final String SYSTEM_DISK_USAGE = "system.disk.usage";

    public static final String DB_POOL_USAGE = "db.pool.usage";

    public static final String DB_POOL_PENDING = "db.pool.pending";

    public static final String BUSINESS_API_ERROR_RATE = "business.api.error.rate";

    private static final Set<String> DERIVED = Set.of(HTTP_ERROR_RATE, HTTP_ERROR_RATE_4XX, HTTP_ERROR_RATE_5XX,
            JVM_HEAP_USAGE, SYSTEM_DISK_USAGE, DB_POOL_USAGE, DB_POOL_PENDING, BUSINESS_API_ERROR_RATE);

    private final MeterRegistry registry;

    private final Map<String, double[]> rateSamples = new ConcurrentHashMap<>();

    public MeterRegistryAlertMetricSource(MeterRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("MeterRegistry 不能为空");
        }
        this.registry = registry;
    }

    @Override
    public Double value(String metric) {
        if (metric == null || metric.isBlank()) {
            return null;
        }
        String key = metric.trim();
        switch (key) {
            case HTTP_ERROR_RATE -> {
                return httpErrorRate(null);
            }
            case HTTP_ERROR_RATE_4XX -> {
                return httpErrorRate("4");
            }
            case HTTP_ERROR_RATE_5XX -> {
                return httpErrorRate("5");
            }
            case JVM_HEAP_USAGE -> {
                return heapUsage();
            }
            case SYSTEM_DISK_USAGE -> {
                return maxGauge("modality.system.disk.usage");
            }
            case DB_POOL_USAGE -> {
                return poolUsage();
            }
            case DB_POOL_PENDING -> {
                Double pending = maxGauge("hikaricp_connections_pending");
                return pending == null ? maxGauge("modality.db.pool.pending") : pending;
            }
            case BUSINESS_API_ERROR_RATE -> {
                return businessApiErrorRate();
            }
            default -> {
            }
        }
        if (key.startsWith("rate(") && key.endsWith(")")) {
            return counterRate(key.substring(5, key.length() - 1).trim());
        }
        return singleValue(key);
    }

    @Override
    public Set<String> metrics() {
        Set<String> names = new LinkedHashSet<>(DERIVED);
        for (io.micrometer.core.instrument.Meter meter : registry.getMeters()) {
            names.add(meter.getId().getName());
        }
        return names;
    }

    private Double httpErrorRate(String statusPrefix) {
        Collection<Timer> timers = registry.find("http.server.requests").timers();
        double total = 0d;
        double errors = 0d;
        for (Timer timer : timers) {
            double count = timer.count();
            if (count <= 0d) {
                continue;
            }
            total += count;
            String status = timer.getId().getTag("status");
            if (status == null || status.isBlank()) {
                continue;
            }
            if (statusPrefix == null) {
                if (status.startsWith("4") || status.startsWith("5")) {
                    errors += count;
                }
            } else if (status.startsWith(statusPrefix)) {
                errors += count;
            }
        }
        return total <= 0d ? null : errors / total;
    }

    private Double heapUsage() {
        double used = sumGauges("jvm.memory.used", "area", "heap");
        double max = sumGauges("jvm.memory.max", "area", "heap");
        return max <= 0d ? null : used / max;
    }

    private Double poolUsage() {
        Map<String, Double> active = firstNonEmpty(taggedGauges("hikaricp_connections_active"),
                taggedGauges("modality.db.pool.active"));
        Map<String, Double> max = firstNonEmpty(taggedGauges("hikaricp_connections_max"),
                taggedGauges("modality.db.pool.max"));
        Double highest = null;
        for (Map.Entry<String, Double> entry : max.entrySet()) {
            Double current = active.get(entry.getKey());
            if (current == null || entry.getValue() <= 0d) {
                continue;
            }
            double usage = current / entry.getValue();
            highest = highest == null ? usage : Math.max(highest, usage);
        }
        return highest;
    }

    private Double businessApiErrorRate() {
        double success = counterValue(BusinessMetrics.API_CALL, "result", "success");
        double failure = counterValue(BusinessMetrics.API_CALL, "result", "failure");
        double total = success + failure;
        return total <= 0d ? null : failure / total;
    }

    private Double counterRate(String name) {
        double total = registry.find(name).counters().stream().mapToDouble(Counter::count).sum();
        long now = System.currentTimeMillis();
        double[] previous = rateSamples.put(name, new double[]{total, now});
        if (previous == null) {
            return null;
        }
        double elapsedSeconds = (now - previous[1]) / 1000d;
        return elapsedSeconds <= 0d ? null : Math.max(total - previous[0], 0d) / elapsedSeconds;
    }

    private Double singleValue(String name) {
        Double gauge = maxGauge(name);
        if (gauge != null) {
            return gauge;
        }
        Collection<Counter> counters = registry.find(name).counters();
        return counters.isEmpty() ? null : counters.stream().mapToDouble(Counter::count).sum();
    }

    private Double maxGauge(String name) {
        Double highest = null;
        for (Gauge gauge : registry.find(name).gauges()) {
            double value = gauge.value();
            if (Double.isNaN(value)) {
                continue;
            }
            highest = highest == null ? value : Math.max(highest, value);
        }
        return highest;
    }

    private double sumGauges(String name, String... tags) {
        double sum = 0d;
        for (Gauge gauge : registry.find(name).tags(tags).gauges()) {
            double value = gauge.value();
            if (!Double.isNaN(value)) {
                sum += value;
            }
        }
        return sum;
    }

    private Map<String, Double> taggedGauges(String name) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Gauge gauge : registry.find(name).gauges()) {
            String pool = gauge.getId().getTag("pool");
            String type = gauge.getId().getTag("type");
            values.put((pool == null ? "default" : pool) + "/" + (type == null ? "default" : type), gauge.value());
        }
        return values;
    }

    private static Map<String, Double> firstNonEmpty(Map<String, Double> primary, Map<String, Double> fallback) {
        return primary.isEmpty() ? fallback : primary;
    }

    private double counterValue(String name, String... tags) {
        return registry.find(name).tags(tags).counters().stream().mapToDouble(Counter::count).sum();
    }
}
