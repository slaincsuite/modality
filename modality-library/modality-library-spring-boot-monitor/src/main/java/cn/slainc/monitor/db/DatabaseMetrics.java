package cn.slainc.monitor.db;

import cn.slainc.monitor.metrics.MetricsTemplate;
import io.micrometer.core.instrument.Timer;

import java.util.function.Supplier;

public class DatabaseMetrics {

    public static final String QUERY = "modality.db.query";

    public static final String SLOW_QUERY = "modality.db.query.slow";

    public static final String TRANSACTION = "modality.db.transaction";

    public static final String QUERY_DURATION = "modality.db.query.duration";

    private final MetricsTemplate metrics;

    public DatabaseMetrics(MetricsTemplate metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("MetricsTemplate 不能为空");
        }
        this.metrics = metrics;
        metrics.counter(QUERY);
        metrics.counter(SLOW_QUERY);
        metrics.counter(TRANSACTION, "result", "commit");
        metrics.counter(TRANSACTION, "result", "rollback");
    }

    public void recordQuery() {
        metrics.increment(QUERY);
    }

    public void recordSlowQuery() {
        metrics.increment(SLOW_QUERY);
    }

    public void recordCommit() {
        metrics.increment(TRANSACTION, "result", "commit");
    }

    public void recordRollback() {
        metrics.increment(TRANSACTION, "result", "rollback");
    }

    public Timer.Sample startQuery() {
        return metrics.startSample();
    }

    public long finishQuery(Timer.Sample sample) {
        recordQuery();
        return metrics.stopSample(sample, QUERY_DURATION);
    }

    public <T> T recordQuery(Supplier<T> supplier) {
        recordQuery();
        return metrics.record(QUERY_DURATION, supplier);
    }

    public double queryCount() {
        return metrics.counterValue(QUERY);
    }

    public double slowQueryCount() {
        return metrics.counterValue(SLOW_QUERY);
    }
}
