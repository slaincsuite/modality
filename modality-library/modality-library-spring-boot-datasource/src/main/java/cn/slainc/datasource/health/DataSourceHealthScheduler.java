package cn.slainc.datasource.health;

import cn.slainc.datasource.failover.DataSourceFailoverManager;
import cn.slainc.datasource.manager.DataSourceHealth;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import cn.slainc.datasource.properties.DataSourceProperties;
import cn.slainc.utils.thread.ThreadPoolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DataSourceHealthScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DataSourceHealthScheduler.class);

    private final DynamicDataSourceManager manager;

    private final DataSourceHealthChecker healthChecker;

    private final DataSourceFailoverManager failoverManager;

    private final DataSourceProperties properties;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private ScheduledExecutorService executor;

    public DataSourceHealthScheduler(DynamicDataSourceManager manager, DataSourceHealthChecker healthChecker,
                                     DataSourceFailoverManager failoverManager,
                                     DataSourceProperties properties) {
        this.manager = manager;
        this.healthChecker = healthChecker;
        this.failoverManager = failoverManager;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (!properties.getHealth().isEnabled() || !running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(
                ThreadPoolUtils.namedThreadFactory("modality-datasource-health"));
        long interval = Math.max(properties.getHealth().getInterval(), 5_000L);
        executor.scheduleWithFixedDelay(this::probe, interval, interval, TimeUnit.MILLISECONDS);
        log.info("数据源健康检查已启动，间隔 {} 毫秒", interval);
    }

    public void probe() {
        try {
            for (Map.Entry<String, DataSource> entry : manager.dataSources().entrySet()) {
                DataSourceHealth health = healthChecker.check(entry.getKey(), entry.getValue());
                manager.recordHealth(health);
                failoverManager.observe(health);
            }
        } catch (Exception exception) {
            log.warn("数据源健康检查异常: {}", exception.getMessage());
        }
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
