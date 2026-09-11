package cn.slainc.captcha.autoconfigure;

import cn.slainc.captcha.CaptchaMetrics;
import cn.slainc.captcha.CaptchaMetricsSnapshot;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.function.ToLongFunction;

public class CaptchaMicrometerBinder {

    private final CaptchaMetrics metrics;

    private final MeterRegistry registry;

    public CaptchaMicrometerBinder(CaptchaMetrics metrics, MeterRegistry registry) {
        this.metrics = metrics;
        this.registry = registry;
        bind("modality.captcha.generated", CaptchaMetricsSnapshot::generated);
        bind("modality.captcha.verify.success", CaptchaMetricsSnapshot::verifySuccess);
        bind("modality.captcha.verify.failure", CaptchaMetricsSnapshot::verifyFailure);
        bind("modality.captcha.locked", CaptchaMetricsSnapshot::locked);
    }

    private void bind(String name, ToLongFunction<CaptchaMetricsSnapshot> extractor) {
        Gauge.builder(name, metrics, m -> (double) extractor.applyAsLong(m.snapshot())).register(registry);
    }
}
