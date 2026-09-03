package cn.slainc.monitor.metrics;

import io.micrometer.core.instrument.Counter;

import java.util.concurrent.atomic.AtomicInteger;

public class HttpConcurrencyMetrics {

    public static final String ACTIVE = "modality.http.requests.active";

    public static final String PEAK = "modality.http.requests.peak";

    public static final String ERRORS = "modality.http.requests.errors";

    private final AtomicInteger active = new AtomicInteger();

    private final AtomicInteger peak = new AtomicInteger();

    private final Counter clientErrors;

    private final Counter serverErrors;

    public HttpConcurrencyMetrics(MetricsTemplate metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("MetricsTemplate 不能为空");
        }
        metrics.gauge(ACTIVE, "当前正在处理的 HTTP 请求数", active);
        metrics.gauge(PEAK, "HTTP 并发请求峰值", peak);
        this.clientErrors = metrics.counter(ERRORS, "type", "client");
        this.serverErrors = metrics.counter(ERRORS, "type", "server");
    }

    public void onRequestStart() {
        int current = active.incrementAndGet();
        peak.accumulateAndGet(current, Math::max);
    }

    public void onRequestFinish() {
        active.updateAndGet(value -> value > 0 ? value - 1 : 0);
    }

    public void onResponse(int status) {
        if (status >= 500) {
            serverErrors.increment();
        } else if (status >= 400) {
            clientErrors.increment();
        }
    }

    public int activeCount() {
        return active.get();
    }

    public int peakCount() {
        return peak.get();
    }
}
