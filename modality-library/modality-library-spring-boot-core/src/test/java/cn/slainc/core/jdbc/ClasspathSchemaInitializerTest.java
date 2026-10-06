package cn.slainc.core.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClasspathSchemaInitializerTest {

    private DriverManagerDataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    private void boot() {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:schemainit;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_schema_sample");
    }

    @Test
    void initCreatesTableFromClasspathScript() throws Exception {
        boot();
        ClasspathSchemaInitializer initializer =
                new ClasspathSchemaInitializer(dataSource, "cn/slainc/core/jdbc/schema-initializer-test.sql");
        initializer.init();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_schema_sample", Integer.class);
        assertEquals(0, count);
    }

    @Test
    void afterPropertiesSetTriggersInit() throws Exception {
        boot();
        ClasspathSchemaInitializer initializer =
                new ClasspathSchemaInitializer(dataSource, "cn/slainc/core/jdbc/schema-initializer-test.sql");
        initializer.afterPropertiesSet();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_schema_sample", Integer.class);
        assertEquals(0, count);
    }

    @Test
    void initIsIdempotentWhenRunTwice() throws Exception {
        boot();
        ClasspathSchemaInitializer initializer =
                new ClasspathSchemaInitializer(dataSource, "cn/slainc/core/jdbc/schema-initializer-test.sql");
        initializer.init();
        initializer.init();
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_schema_sample", Integer.class);
        assertEquals(0, count);
    }

    @Test
    void subclassCanExtendInit() throws Exception {
        boot();
        ClasspathSchemaInitializer initializer = new ClasspathSchemaInitializer(
                dataSource, "cn/slainc/core/jdbc/schema-initializer-test.sql") {
            @Override
            public void init() throws Exception {
                try (var connection = dataSource.getConnection();
                        var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS t_schema_extra (id BIGINT PRIMARY KEY)");
                }
                super.init();
            }
        };
        initializer.init();
        Integer sample = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_schema_sample", Integer.class);
        Integer extra = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_schema_extra", Integer.class);
        assertEquals(0, sample);
        assertEquals(0, extra);
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_schema_extra");
    }
}
