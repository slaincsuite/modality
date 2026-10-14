package cn.slainc.job.support;

import cn.slainc.job.api.impl.JobSchedulerImpl;
import cn.slainc.job.autoconfigure.JobProperties;
import cn.slainc.job.enums.MisfirePolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class QuartzJobStoreTest {

    private static final List<String> QUARTZ_TABLES = List.of(
            "QRTZ_JOB_DETAILS", "QRTZ_TRIGGERS", "QRTZ_SIMPLE_TRIGGERS", "QRTZ_CRON_TRIGGERS",
            "QRTZ_SIMPROP_TRIGGERS", "QRTZ_BLOB_TRIGGERS", "QRTZ_CALENDARS", "QRTZ_PAUSED_TRIGGER_GRPS",
            "QRTZ_FIRED_TRIGGERS", "QRTZ_SCHEDULER_STATE", "QRTZ_LOCKS");

    @Test
    void shouldCreateQuartzSchemaFromTheOfficialScript() throws Exception {
        JdbcDataSource dataSource = QuartzTestSupport.h2DataSource("quartz-schema-" + UUID.randomUUID());

        QuartzTestSupport.applyQuartzSchema(dataSource);

        assertThat(tableNames(dataSource)).containsAll(QUARTZ_TABLES);
    }

    @Test
    void shouldFireJobThroughTheQuartzBridgeOnJdbcStore() throws Exception {
        JdbcDataSource dataSource = QuartzTestSupport.h2DataSource("quartz-bridge-" + UUID.randomUUID());
        QuartzTestSupport.applyQuartzSchema(dataSource);
        Scheduler quartz = QuartzTestSupport.jdbcScheduler(dataSource, true, 60_000L);
        JobSchedulerImpl scheduler = new JobSchedulerImpl(null, null, new JobProperties(), quartz);
        CountDownLatch fired = new CountDownLatch(1);
        try {
            scheduler.register(new JobDefinition("jdbc-tick", "* * * * * ?", null, 0, Duration.ZERO, null, true,
                    MisfirePolicy.SKIP, context -> {
                        fired.countDown();
                        return JobResult.success(0L);
                    }));

            quartz.start();

            assertThat(fired.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(quartz.getMetaData().getJobStoreClass().getName()).contains("JobStore");
        } finally {
            scheduler.destroy();
            quartz.shutdown(true);
        }
    }

    @Test
    void shouldPersistConvertedCronAndMisfireInstruction() throws Exception {
        JdbcDataSource dataSource = QuartzTestSupport.h2DataSource("quartz-cron-" + UUID.randomUUID());
        QuartzTestSupport.applyQuartzSchema(dataSource);
        Scheduler quartz = QuartzTestSupport.jdbcScheduler(dataSource, true, 60_000L);
        JobSchedulerImpl scheduler = new JobSchedulerImpl(null, null, new JobProperties(), quartz);
        try {
            scheduler.register(new JobDefinition("jdbc-converted", "0 0 2 * * *", null, 0, Duration.ZERO, null, true,
                    MisfirePolicy.FIRE_ONCE_NOW, context -> JobResult.success(0L)));

            assertThat(triggerColumn(dataSource, "jdbc-converted", "CRON_EXPRESSION")).isEqualTo("0 0 2 * * ?");
            assertThat(triggerColumn(dataSource, "jdbc-converted", "MISFIRE_INSTR"))
                    .isEqualTo(String.valueOf(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW));
        } finally {
            scheduler.destroy();
            quartz.shutdown(true);
        }
    }

    @Test
    void shouldNotCompensateStartupOnJdbcJobStore() throws Exception {
        JdbcDataSource dataSource = QuartzTestSupport.h2DataSource("quartz-compensation-" + UUID.randomUUID());
        QuartzTestSupport.applyQuartzSchema(dataSource);
        Scheduler quartz = QuartzTestSupport.jdbcScheduler(dataSource, true, 60_000L);
        JobSchedulerImpl scheduler = new JobSchedulerImpl(null, null, new JobProperties(), quartz);
        AtomicInteger counter = new AtomicInteger();
        try {
            scheduler.registerOnStartup(new JobDefinition("jdbc-once-now", "0 0 3 * * ?", null, 0, Duration.ZERO,
                    context -> {
                        counter.incrementAndGet();
                        return JobResult.success(0L);
                    }));

            assertThat(counter.get()).isZero();
        } finally {
            scheduler.destroy();
            quartz.shutdown(true);
        }
    }

    @Test
    void shouldFireEachScheduledTimeOnlyOnceAcrossClusteredNodes() throws Exception {
        JdbcDataSource dataSource = QuartzTestSupport.h2DataSource("quartz-cluster-" + UUID.randomUUID());
        QuartzTestSupport.applyQuartzSchema(dataSource);
        Scheduler nodeA = QuartzTestSupport.jdbcScheduler(dataSource, true, 500L);
        Scheduler nodeB = QuartzTestSupport.jdbcScheduler(dataSource, true, 500L);
        CountingJob.reset();
        try {
            JobDetail job = JobBuilder.newJob(CountingJob.class)
                    .withIdentity("cluster-tick", "cluster-test")
                    .storeDurably()
                    .build();
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("cluster-tick", "cluster-test")
                    .forJob(job)
                    .withSchedule(CronScheduleBuilder.cronSchedule("* * * * * ?")
                            .withMisfireHandlingInstructionDoNothing())
                    .build();
            nodeA.scheduleJob(job, Set.of(trigger), true);

            nodeA.start();
            nodeB.start();
            Thread.sleep(4000L);

            int checkins = scalar(dataSource, "SELECT COUNT(*) FROM QRTZ_SCHEDULER_STATE");
            assertThat(checkins).as("两个节点都应向 QRTZ_SCHEDULER_STATE 报到").isEqualTo(2);
            assertThat(CountingJob.fired.get()).as("4 秒窗口内应当真的触发过").isGreaterThanOrEqualTo(2);
            assertThat(CountingJob.duplicates.get())
                    .as("同一个调度触发点不能被两个节点各执行一次（Quartz 集群 DB 行锁互斥）")
                    .isZero();
        } finally {
            nodeA.shutdown(true);
            nodeB.shutdown(true);
        }
    }

    public static class CountingJob implements Job {

        static final AtomicInteger fired = new AtomicInteger();

        static final AtomicInteger duplicates = new AtomicInteger();

        private static final Set<Long> SCHEDULED = ConcurrentHashMap.newKeySet();

        static void reset() {
            fired.set(0);
            duplicates.set(0);
            SCHEDULED.clear();
        }

        @Override
        public void execute(JobExecutionContext context) {
            fired.incrementAndGet();
            if (!SCHEDULED.add(context.getScheduledFireTime().getTime())) {
                duplicates.incrementAndGet();
            }
        }
    }

    private static List<String> tableNames(JdbcDataSource dataSource) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                names.add(resultSet.getString(1).toUpperCase());
            }
        }
        return names;
    }

    private static String triggerColumn(JdbcDataSource dataSource, String triggerName, String column)
            throws SQLException {
        String sql = "SELECT " + column + " FROM QRTZ_CRON_TRIGGERS c JOIN QRTZ_TRIGGERS t"
                + " ON c.SCHED_NAME = t.SCHED_NAME AND c.TRIGGER_NAME = t.TRIGGER_NAME"
                + " AND c.TRIGGER_GROUP = t.TRIGGER_GROUP WHERE t.TRIGGER_NAME = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, triggerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static int scalar(JdbcDataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : -1;
        }
    }
}
