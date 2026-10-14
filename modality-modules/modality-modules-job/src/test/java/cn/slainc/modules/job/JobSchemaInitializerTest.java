package cn.slainc.modules.job;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 守护 job-schema.sql 在 H2 MySQL 兼容模式与 MySQL 上同样可执行（含 Quartz 集群表）
class JobSchemaInitializerTest {

    @Test
    void schemaCreatesTaskLogAndQuartzTablesOnH2() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:jobschema;DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");

        new ClasspathSchemaInitializer(dataSource, "job-schema.sql").init();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'", String.class);
        assertThat(tables).contains("T_JOB_TASK", "T_JOB_LOG",
                "QRTZ_JOB_DETAILS", "QRTZ_TRIGGERS", "QRTZ_LOCKS", "QRTZ_SCHEDULER_STATE", "QRTZ_CALENDARS");
    }
}
