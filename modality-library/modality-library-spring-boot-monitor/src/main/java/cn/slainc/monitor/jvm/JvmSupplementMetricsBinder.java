package cn.slainc.monitor.jvm;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.JvmThreadDeadlockMetrics;

public class JvmSupplementMetricsBinder implements MeterBinder {

    @Override
    public void bindTo(MeterRegistry registry) {
        // Boot 的 JvmMetricsAutoConfiguration 没有绑定死锁线程指标，这里补上 jvm.threads.deadlocked 系列
        new JvmThreadDeadlockMetrics().bindTo(registry);
    }
}
