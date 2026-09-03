package cn.slainc.monitor.db;

import cn.slainc.monitor.metrics.MetricsTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractPoolMetricsBinder implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(AbstractPoolMetricsBinder.class);

    protected static final String ACTIVE = "modality.db.pool.active";

    protected static final String IDLE = "modality.db.pool.idle";

    protected static final String PENDING = "modality.db.pool.pending";

    protected static final String TOTAL = "modality.db.pool.total";

    protected static final String MAX = "modality.db.pool.max";

    protected static final String POOL_COUNT = "modality.db.pool.count";

    private final ObjectProvider<DataSource> dataSources;

    private final DataSourcePoolIntrospector introspector;

    private final long refreshMillis;

    private final Map<String, PoolHolder> pools = new ConcurrentHashMap<>();

    private final AtomicBoolean warned = new AtomicBoolean();

    private volatile long refreshedAt;

    protected final MetricsTemplate metrics;

    protected AbstractPoolMetricsBinder(ObjectProvider<DataSource> dataSources, DataSourcePoolIntrospector introspector,
                                        MetricsTemplate metrics, long refreshMillis) {
        this.dataSources = dataSources;
        this.introspector = introspector;
        this.metrics = metrics;
        this.refreshMillis = Math.max(refreshMillis, 1000L);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        metrics.gauge(POOL_COUNT, "已发现的数据库连接池数量", this, AbstractPoolMetricsBinder::poolCount,
                "type", type());
    }

    protected abstract String type();

    protected abstract PoolSnapshot snapshot(String pool, DataSource dataSource);

    protected void registerAdditionalGauges(String pool, DataSource dataSource) {
    }

    protected void warnUnavailable(String message) {
        if (warned.compareAndSet(false, true)) {
            log.warn(message);
        }
    }

    private double poolCount() {
        refreshIfStale();
        return pools.size();
    }

    private void refreshIfStale() {
        if (System.currentTimeMillis() - refreshedAt >= refreshMillis) {
            refresh();
        }
    }

    private synchronized void refresh() {
        refreshedAt = System.currentTimeMillis();
        Map<String, DataSource> resolved = new LinkedHashMap<>();
        try {
            dataSources.orderedStream().forEach(dataSource -> resolved.putAll(introspector.flatten(List.of(dataSource))));
        } catch (RuntimeException exception) {
            log.warn("读取数据源失败，连接池指标本次不刷新: {}", exception.getMessage());
            return;
        }
        for (Map.Entry<String, DataSource> entry : resolved.entrySet()) {
            PoolSnapshot snapshot = snapshot(entry.getKey(), entry.getValue());
            if (snapshot == null) {
                continue;
            }
            String pool = snapshot.name() == null || snapshot.name().isBlank() ? entry.getKey() : snapshot.name();
            PoolHolder holder = pools.get(pool);
            if (holder == null) {
                holder = new PoolHolder(pool);
                PoolHolder created = holder;
                metrics.gauge(ACTIVE, "连接池活跃连接数", created, PoolHolder::active, "pool", pool, "type", type());
                metrics.gauge(IDLE, "连接池空闲连接数", created, PoolHolder::idle, "pool", pool, "type", type());
                metrics.gauge(PENDING, "等待获取连接的线程数", created, PoolHolder::pending, "pool", pool, "type", type());
                metrics.gauge(TOTAL, "连接池连接总数", created, PoolHolder::total, "pool", pool, "type", type());
                metrics.gauge(MAX, "连接池最大连接数", created, PoolHolder::max, "pool", pool, "type", type());
                registerAdditionalGauges(pool, entry.getValue());
                pools.put(pool, holder);
            }
            holder.update(snapshot);
        }
    }

    private static final class PoolHolder {

        private final String name;

        private volatile PoolSnapshot snapshot;

        private PoolHolder(String name) {
            this.name = name;
            this.snapshot = PoolSnapshot.empty(name);
        }

        private void update(PoolSnapshot value) {
            this.snapshot = value;
        }

        private double active() {
            return snapshot.active();
        }

        private double idle() {
            return snapshot.idle();
        }

        private double pending() {
            return snapshot.pending();
        }

        private double total() {
            return snapshot.total();
        }

        private double max() {
            return snapshot.max();
        }
    }
}
