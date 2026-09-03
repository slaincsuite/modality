package cn.slainc.monitor.satoken;

import cn.slainc.monitor.metrics.MetricsTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

public class SaTokenMetricsBinder implements MeterBinder {

    private final SaTokenMetricsSource source;

    private final MetricsTemplate metrics;

    private final String instance;

    public SaTokenMetricsBinder(SaTokenMetricsSource source, MetricsTemplate metrics, String instance) {
        if (source == null) {
            throw new IllegalArgumentException("SaTokenMetricsSource 不能为空");
        }
        this.source = source;
        this.metrics = metrics;
        this.instance = instance == null || instance.isBlank() ? "default" : instance;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        metrics.gauge("modality.satoken.online.users", "Sa-Token 在线用户数", source,
                SaTokenMetricsSource::onlineUsers, "instance", instance);
        metrics.gauge("modality.satoken.tokens", "Sa-Token 有效 Token 数", source,
                SaTokenMetricsSource::tokenCount, "instance", instance);
    }
}
