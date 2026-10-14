package cn.slainc.job.support;

import cn.slainc.job.autoconfigure.JobProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

// Quartz 存储定制：按配置选择 JDBC 或 RAM 存储，并推迟自动启动到任务定义同步完成后
public class QuartzStoreCustomizer implements SchedulerFactoryBeanCustomizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuartzStoreCustomizer.class);

    private static final String JOB_STORE_IS_CLUSTERED = "org.quartz.jobStore.isClustered";

    private static final String SCHEDULER_INSTANCE_ID = "org.quartz.scheduler.instanceId";

    private static final String SCHEMA_OWNER_HINT = "QRTZ_* 表需由部署方按 Quartz 官方 tables_*.sql 预先创建";

    private final JobProperties properties;

    private final QuartzProperties quartzProperties;

    private final DataSource dataSource;

    private boolean jdbcStore;

    private boolean startupDeferred;

    public QuartzStoreCustomizer(JobProperties properties, QuartzProperties quartzProperties, DataSource dataSource) {
        this.properties = properties;
        this.quartzProperties = quartzProperties == null ? new QuartzProperties() : quartzProperties;
        this.dataSource = dataSource;
    }

    @Override
    public void customize(SchedulerFactoryBean factory) {
        this.jdbcStore = useJdbcStore();
        if (jdbcStore) {
            factory.setDataSource(dataSource);
            factory.setQuartzProperties(quartzProperties());
            LOGGER.info("Quartz 存储模式 = JDBC（QRTZ_* 表），集群={}, {}", properties.isClustered(), SCHEMA_OWNER_HINT);
        } else {
            LOGGER.warn("Quartz 存储模式 = RAMJobStore：重启丢失触发状态，集群互斥仅依赖 Redis 锁；"
                    + "提供 DataSource（或设置 modality.job.store=jdbc）可持久化触发器");
        }
        if (factory.isAutoStartup()) {
            factory.setAutoStartup(false);
            this.startupDeferred = true;
            LOGGER.info("Quartz 自动启动已推迟到任务定义同步完成后");
        }
    }

    public boolean isJdbcStore() {
        return jdbcStore;
    }

    public boolean isStartupDeferred() {
        return startupDeferred;
    }

    private boolean useJdbcStore() {
        JobProperties.StoreType store = properties.getStore() == null ? JobProperties.StoreType.AUTO : properties.getStore();
        return switch (store) {
            case MEMORY -> false;
            case JDBC -> {
                if (dataSource == null) {
                    throw new IllegalStateException("modality.job.store=jdbc 需要容器中存在 DataSource Bean");
                }
                yield true;
            }
            case AUTO -> dataSource != null;
        };
    }

    private Properties quartzProperties() {
        Properties merged = new Properties();
        merged.putAll(quartzProperties.getProperties());
        merged.putIfAbsent(JOB_STORE_IS_CLUSTERED, Boolean.toString(properties.isClustered()));
        merged.putIfAbsent(SCHEDULER_INSTANCE_ID, "AUTO");
        return merged;
    }
}
