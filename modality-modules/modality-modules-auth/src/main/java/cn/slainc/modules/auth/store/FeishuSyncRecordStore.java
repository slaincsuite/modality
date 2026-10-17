package cn.slainc.modules.auth.store;

import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.third.FeishuSyncRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class FeishuSyncRecordStore {

    private static final String TABLE_NAME = AuthKeys.THIRD_SYNC_RECORD_TABLE;

    private static final RowMapper<FeishuSyncRecord> MAPPER = (rs, rowNum) -> {
        FeishuSyncRecord record = new FeishuSyncRecord();
        record.setId(rs.getLong("id"));
        record.setPlatform(rs.getString("platform"));
        record.setSyncType(rs.getString("sync_type"));
        record.setStatus(rs.getString("status"));
        record.setTotalCount(rs.getInt("total_count"));
        record.setSuccessCount(rs.getInt("success_count"));
        record.setFailureCount(rs.getInt("failure_count"));
        record.setMessage(rs.getString("message"));
        record.setOperator(rs.getString("operator"));
        record.setStartedAt(rs.getLong("started_at"));
        long finishedAt = rs.getLong("finished_at");
        record.setFinishedAt(rs.wasNull() ? null : finishedAt);
        record.setCreateTime(rs.getLong("create_time"));
        return record;
    };

    private final JdbcTemplate jdbcTemplate;

    public FeishuSyncRecordStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FeishuSyncRecord start(FeishuSyncRecord record) {
        long now = System.currentTimeMillis();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO " + TABLE_NAME
                            + " (platform, sync_type, status, total_count, success_count, failure_count, message,"
                            + " operator, started_at, finished_at, create_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, record.getPlatform());
            statement.setString(2, record.getSyncType());
            statement.setString(3, record.getStatus());
            statement.setInt(4, value(record.getTotalCount()));
            statement.setInt(5, value(record.getSuccessCount()));
            statement.setInt(6, value(record.getFailureCount()));
            statement.setString(7, record.getMessage());
            statement.setString(8, record.getOperator());
            statement.setLong(9, record.getStartedAt());
            if (record.getFinishedAt() == null) {
                statement.setNull(10, java.sql.Types.BIGINT);
            } else {
                statement.setLong(10, record.getFinishedAt());
            }
            statement.setLong(11, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            record.setId(key.longValue());
        }
        record.setCreateTime(now);
        return record;
    }

    public void finish(Long id, String status, int totalCount, int successCount, int failureCount, String message,
                       long finishedAt) {
        jdbcTemplate.update("UPDATE " + TABLE_NAME
                        + " SET status = ?, total_count = ?, success_count = ?, failure_count = ?, message = ?,"
                        + " finished_at = ? WHERE id = ?",
                status, totalCount, successCount, failureCount, message, finishedAt, id);
    }

    public long count(String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(TABLE_NAME);
        List<Object> args = new ArrayList<>();
        appendStatus(sql, args, status);
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public List<FeishuSyncRecord> page(String status, long current, long size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE_NAME);
        List<Object> args = new ArrayList<>();
        appendStatus(sql, args, status);
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((current - 1) * size);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public List<FeishuSyncRecord> list(String status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE_NAME);
        List<Object> args = new ArrayList<>();
        appendStatus(sql, args, status);
        sql.append(" ORDER BY id DESC");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public FeishuSyncRecord find(Long id) {
        List<FeishuSyncRecord> records = jdbcTemplate.query("SELECT * FROM " + TABLE_NAME + " WHERE id = ?",
                MAPPER, id);
        return records.isEmpty() ? null : records.get(0);
    }

    private void appendStatus(StringBuilder sql, List<Object> args, String status) {
        if (status != null && !status.isBlank()) {
            sql.append(" WHERE status = ?");
            args.add(status);
        }
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
