package cn.slainc.storage.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MicrometerStorageMetrics implements StorageMetricsRecorder {

    private static final String PREFIX = "modality.storage.";

    private final MeterRegistry registry;

    private final Map<String, AtomicLong> sizes = new ConcurrentHashMap<>();

    public MicrometerStorageMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordUpload(String backend, long bytes, boolean success, long costMillis) {
        counter("upload.count", backend, "结果", success ? "成功" : "失败").increment();
        if (bytes > 0) {
            counter("upload.bytes", backend, "结果", success ? "成功" : "失败").increment(bytes);
        }
        timer("upload.cost", backend).record(costMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordDownload(String backend, long bytes, boolean success, long costMillis) {
        counter("download.count", backend, "结果", success ? "成功" : "失败").increment();
        if (bytes > 0) {
            counter("download.bytes", backend, "结果", success ? "成功" : "失败").increment(bytes);
        }
        timer("download.cost", backend).record(costMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public void recordDelete(String backend, boolean success) {
        counter("delete.count", backend, "结果", success ? "成功" : "失败").increment();
    }

    @Override
    public void recordFailure(String backend, String operation) {
        Counter.builder(PREFIX + "failure.count")
                .tag("backend", backend)
                .tag("operation", operation == null ? "未知" : operation)
                .register(registry)
                .increment();
    }

    @Override
    public void recordInstantUpload(boolean hit) {
        Counter.builder(PREFIX + "instant.count").tag("结果", hit ? "命中" : "未命中").register(registry).increment();
    }

    @Override
    public void recordBackendSwitch(String from, String to) {
        Counter.builder(PREFIX + "backend.switch.count").tag("from", from).tag("to", to).register(registry)
                .increment();
    }

    @Override
    public void recordStorageSize(String backend, long bytes) {
        AtomicLong holder = sizes.computeIfAbsent(backend, name -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder(PREFIX + "size.bytes", value, AtomicLong::get).tag("backend", name).register(registry);
            return value;
        });
        holder.set(bytes);
    }

    @Override
    public void recordMultipart(String backend, int parts) {
        Counter.builder(PREFIX + "multipart.parts").tag("backend", backend).register(registry).increment(parts);
    }

    private Counter counter(String name, String backend, String tagName, String tagValue) {
        return Counter.builder(PREFIX + name).tag("backend", backend).tag(tagName, tagValue).register(registry);
    }

    private io.micrometer.core.instrument.Timer timer(String name, String backend) {
        return io.micrometer.core.instrument.Timer.builder(PREFIX + name).tag("backend", backend)
                .register(registry);
    }
}
