package cn.slainc.monitor.db;

import cn.slainc.monitor.metrics.MetricsTemplate;
import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;

public class DruidPoolMetricsBinder extends AbstractPoolMetricsBinder {

    public static final String EXECUTE_COUNT = "modality.db.druid.execute.total";

    public static final String ERROR_COUNT = "modality.db.druid.error.total";

    public static final String COMMIT_COUNT = "modality.db.druid.commit.total";

    public static final String ROLLBACK_COUNT = "modality.db.druid.rollback.total";

    public DruidPoolMetricsBinder(ObjectProvider<DataSource> dataSources, DataSourcePoolIntrospector introspector,
                                  MetricsTemplate metrics, long refreshMillis) {
        super(dataSources, introspector, metrics, refreshMillis);
    }

    @Override
    protected String type() {
        return "druid";
    }

    @Override
    protected PoolSnapshot snapshot(String pool, DataSource dataSource) {
        if (!(dataSource instanceof DruidDataSource druid)) {
            return null;
        }
        String name = pool == null || pool.isBlank() || pool.startsWith("DruidDataSource") ? druid.getName() : pool;
        if (name == null || name.isBlank()) {
            name = "default";
        }
        double active = druid.getActiveCount();
        double idle = druid.getPoolingCount();
        return new PoolSnapshot(name, active, idle, druid.getWaitThreadCount(), active + idle,
                druid.getMaxActive());
    }

    @Override
    protected void registerAdditionalGauges(String pool, DataSource dataSource) {
        if (!(dataSource instanceof DruidDataSource druid)) {
            return;
        }
        metrics.gauge(EXECUTE_COUNT, "Druid 累计执行 SQL 次数", druid, DruidDataSource::getExecuteCount, "pool", pool);
        metrics.gauge(ERROR_COUNT, "Druid 累计执行失败次数", druid, DruidDataSource::getErrorCount, "pool", pool);
        metrics.gauge(COMMIT_COUNT, "Druid 累计事务提交次数", druid, DruidDataSource::getCommitCount, "pool", pool);
        metrics.gauge(ROLLBACK_COUNT, "Druid 累计事务回滚次数", druid, DruidDataSource::getRollbackCount, "pool", pool);
    }
}
