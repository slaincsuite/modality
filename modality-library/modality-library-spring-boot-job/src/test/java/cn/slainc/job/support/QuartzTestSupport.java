package cn.slainc.job.support;

import org.h2.jdbcx.JdbcDataSource;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;

public final class QuartzTestSupport {

    // Quartz 官方 H2 建表脚本，随 quartz jar 发布
    public static final String QUARTZ_SCHEMA = "org/quartz/impl/jdbcjobstore/tables_h2.sql";

    private QuartzTestSupport() {
    }

    public static Scheduler ramScheduler() {
        return ramScheduler(60_000L);
    }

    public static Scheduler ramScheduler(long misfireThresholdMillis) {
        Properties properties = baseProperties("ram-" + UUID.randomUUID(), misfireThresholdMillis);
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        return newScheduler(properties);
    }

    public static Scheduler jdbcScheduler(DataSource dataSource, boolean clustered, long misfireThresholdMillis) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setAutoStartup(false);
        factory.setOverwriteExistingJobs(true);
        Properties properties = baseProperties("jdbc-" + UUID.randomUUID(), misfireThresholdMillis);
        properties.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        properties.setProperty("org.quartz.jobStore.isClustered", Boolean.toString(clustered));
        factory.setQuartzProperties(properties);
        try {
            factory.afterPropertiesSet();
            return factory.getScheduler();
        } catch (Exception e) {
            throw new IllegalStateException("创建 JDBC Quartz 调度器失败", e);
        }
    }

    public static JdbcDataSource h2DataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    public static void applyQuartzSchema(DataSource dataSource) {
        String script;
        try (var input = new ClassPathResource(QUARTZ_SCHEMA).getInputStream()) {
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 " + QUARTZ_SCHEMA + " 失败", e);
        }
        StringBuilder sql = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            sql.append(line).append('\n');
        }
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String fragment : sql.toString().split(";")) {
                if (!fragment.isBlank()) {
                    statement.execute(fragment);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("执行 Quartz 建表脚本失败", e);
        }
    }

    private static Properties baseProperties(String instanceName, long misfireThresholdMillis) {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", instanceName);
        properties.setProperty("org.quartz.scheduler.instanceId", "NON_CLUSTERED");
        properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.setProperty("org.quartz.threadPool.threadCount", "4");
        properties.setProperty("org.quartz.jobStore.misfireThreshold", Long.toString(misfireThresholdMillis));
        return properties;
    }

    private static Scheduler newScheduler(Properties properties) {
        try {
            return new StdSchedulerFactory(properties).getScheduler();
        } catch (SchedulerException e) {
            throw new IllegalStateException("创建 Quartz 调度器失败", e);
        }
    }
}
