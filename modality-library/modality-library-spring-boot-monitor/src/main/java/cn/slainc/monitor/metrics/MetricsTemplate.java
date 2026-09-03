package cn.slainc.monitor.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

public class MetricsTemplate {

    private final MeterRegistry registry;

    private final MetricNaming naming;

    private final boolean histogram;

    public MetricsTemplate(MeterRegistry registry, MetricNaming naming, boolean histogram) {
        if (registry == null) {
            throw new IllegalArgumentException("MeterRegistry 不能为空");
        }
        this.registry = registry;
        this.naming = naming == null ? new MetricNaming() : naming;
        this.histogram = histogram;
    }

    public MeterRegistry registry() {
        return registry;
    }

    public MetricNaming naming() {
        return naming;
    }

    public String name(String rawName) {
        return naming.normalize(rawName);
    }

    public Counter counter(String rawName, String... tags) {
        return Counter.builder(name(rawName)).tags(Tags.of(tags)).register(registry);
    }

    public void increment(String rawName, String... tags) {
        counter(rawName, tags).increment();
    }

    public void increment(String rawName, double amount, String... tags) {
        counter(rawName, tags).increment(amount);
    }

    // Gauge 内部只持有弱引用，调用方必须自己长期持有 state 的强引用，否则取值会变成 NaN
    public <T> T gauge(String rawName, T state, ToDoubleFunction<T> value, String... tags) {
        Gauge.builder(name(rawName), state, value).tags(Tags.of(tags)).register(registry);
        return state;
    }

    public <T extends Number> T gauge(String rawName, T state, String... tags) {
        Gauge.builder(name(rawName), state, Number::doubleValue).tags(Tags.of(tags)).register(registry);
        return state;
    }

    public <T> T gauge(String rawName, String description, T state, ToDoubleFunction<T> value, String... tags) {
        Gauge.builder(name(rawName), state, value).description(description).tags(Tags.of(tags)).register(registry);
        return state;
    }

    public <T extends Number> T gauge(String rawName, String description, T state, String... tags) {
        Gauge.builder(name(rawName), state, Number::doubleValue).description(description).tags(Tags.of(tags))
                .register(registry);
        return state;
    }

    public Timer timer(String rawName, String... tags) {
        Timer.Builder builder = Timer.builder(name(rawName)).tags(Tags.of(tags));
        if (histogram) {
            builder.publishPercentileHistogram();
        }
        return builder.register(registry);
    }

    public DistributionSummary summary(String rawName, String... tags) {
        DistributionSummary.Builder builder = DistributionSummary.builder(name(rawName)).tags(Tags.of(tags));
        if (histogram) {
            builder.publishPercentileHistogram();
        }
        return builder.register(registry);
    }

    public LongTaskTimer longTaskTimer(String rawName, String... tags) {
        return LongTaskTimer.builder(name(rawName)).tags(Tags.of(tags)).register(registry);
    }

    public Timer.Sample startSample() {
        return Timer.start(registry);
    }

    public long stopSample(Timer.Sample sample, String rawName, String... tags) {
        return sample.stop(timer(rawName, tags));
    }

    public LongTaskTimer.Sample startLongTask(String rawName, String... tags) {
        return longTaskTimer(rawName, tags).start();
    }

    public long stopLongTask(LongTaskTimer.Sample sample) {
        return sample.stop();
    }

    public void record(String rawName, double amount, String... tags) {
        summary(rawName, tags).record(amount);
    }

    public void record(String rawName, Runnable runnable, String... tags) {
        timer(rawName, tags).record(runnable);
    }

    public <T> T record(String rawName, Supplier<T> supplier, String... tags) {
        return timer(rawName, tags).record(supplier);
    }

    public double counterValue(String rawName, String... tags) {
        Counter counter = registry.find(name(rawName)).tags(tags).counter();
        return counter == null ? 0d : counter.count();
    }

    public double gaugeValue(String rawName, String... tags) {
        Gauge gauge = registry.find(name(rawName)).tags(tags).gauge();
        if (gauge == null) {
            return Double.NaN;
        }
        return gauge.value();
    }
}
