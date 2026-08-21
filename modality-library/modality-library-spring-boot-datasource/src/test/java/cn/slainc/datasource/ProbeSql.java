package cn.slainc.datasource;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public final class ProbeSql {

    private static JdbcTemplate jdbcTemplate;

    private ProbeSql() {
    }

    public static void bind(DataSource dataSource) {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public static String label() {
        return jdbcTemplate.queryForObject("SELECT label FROM probe", String.class);
    }

    public static int count() {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM probe", Integer.class);
        return value == null ? 0 : value;
    }

    public static void execute(String sql) {
        jdbcTemplate.execute(sql);
    }

    public static int queryInt(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    public static void prepare(String url, String label) {
        prepare(url, label, "");
    }

    public static void prepare(String url, String label, String password) {
        try (Connection connection = DriverManager.getConnection(url, "sa", password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS probe (label VARCHAR(64))");
            statement.execute("DELETE FROM probe");
            statement.execute("INSERT INTO probe (label) VALUES ('" + label + "')");
        } catch (Exception exception) {
            throw new IllegalStateException("初始化探测库失败: " + url, exception);
        }
    }
}
