package cn.slainc.modules.open.store;

import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.entity.AppLogRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

public class JdbcAppLogStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAppLogStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AppLogRecord append(String appId, String action, String operator, String reason, String detail) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        long now = System.currentTimeMillis();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + OpenKeys.APP_LOG_TABLE
                            + " (app_id, action, operator, reason, detail, create_time) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, appId);
            statement.setString(2, action);
            statement.setString(3, operator);
            statement.setString(4, reason);
            statement.setString(5, detail);
            statement.setLong(6, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return new AppLogRecord(key == null ? null : key.longValue(), appId, action, operator, reason, detail, now);
    }

    public List<AppLogRecord> listByApp(String appId, int limit) {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.APP_LOG_TABLE
                        + " WHERE app_id = ? ORDER BY create_time DESC, id DESC LIMIT " + Math.max(limit, 1),
                (rs, rowNum) -> new AppLogRecord(rs.getLong("id"), rs.getString("app_id"), rs.getString("action"),
                        rs.getString("operator"), rs.getString("reason"), rs.getString("detail"),
                        rs.getLong("create_time")), appId);
    }
}
