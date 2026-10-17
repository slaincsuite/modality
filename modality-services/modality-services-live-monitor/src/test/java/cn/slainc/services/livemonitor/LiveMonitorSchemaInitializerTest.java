package cn.slainc.services.livemonitor;

import cn.slainc.core.jdbc.ClasspathSchemaInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LiveMonitorSchemaInitializerTest {

    @Test
    void schemaCreatesTenantStreamTableOnH2() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:livemonitorschema;DB_CLOSE_DELAY=-1;MODE=MySQL", "sa", "");

        new ClasspathSchemaInitializer(dataSource, "live-monitor-schema.sql").init();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'PUBLIC' AND table_name = 'T_LIVE_STREAM'", String.class);
        assertThat(columns).contains("TENANT_ID", "NAME", "URL", "ENABLED", "STATUS", "LAST_CHECK_AT",
                "LAST_LATENCY_MS", "LAST_ERROR", "CREATE_TIME", "UPDATE_TIME", "DELETED");
    }
}
