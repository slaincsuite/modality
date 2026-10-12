package cn.slainc.modules.auth.store;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.closure.AccountClosureRecord;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

public class AccountClosureStore {

    private static final String TABLE_NAME = AuthKeys.ACCOUNT_CLOSURE_TABLE;

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<AccountClosureRecord> MAPPER = (rs, rowNum) -> {
        AccountClosureRecord record = new AccountClosureRecord();
        record.setId(rs.getLong("id"));
        long tenantId = rs.getLong("tenant_id");
        record.setTenantId(rs.wasNull() ? null : tenantId);
        record.setUserId(rs.getLong("user_id"));
        record.setLoginType(rs.getString("login_type"));
        record.setUsername(rs.getString("username"));
        record.setReason(rs.getString("reason"));
        record.setStatus(rs.getString("status"));
        record.setRequestedAt(rs.getLong("requested_at"));
        record.setCoolDownUntil(rs.getLong("cool_down_until"));
        record.setAttemptCount(rs.getInt("attempt_count"));
        long executedAt = rs.getLong("executed_at");
        record.setExecutedAt(rs.wasNull() ? null : executedAt);
        record.setExecuteError(rs.getString("execute_error"));
        return record;
    };

    public AccountClosureStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AccountClosureRecord insert(AccountClosureRecord record) {
        return insertIfAbsent(record).orElseThrow(() -> new IllegalStateException("已存在待执行的注销申请，冷静期内可撤销"));
    }

    // 条件插入：INSERT ... SELECT ... WHERE NOT EXISTS 单条语句完成判重，同一用户的并发申请只会有一条真正落库
    public Optional<AccountClosureRecord> insertIfAbsent(AccountClosureRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + TABLE_NAME + "(tenant_id, user_id, login_type, username, reason, status,"
                            + " requested_at, cool_down_until) SELECT ?,?,?,?,?,?,?,? FROM (SELECT 1) anchor"
                            + " WHERE NOT EXISTS (SELECT 1 FROM " + TABLE_NAME + " WHERE user_id = ? AND status = ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, record.getTenantId());
            statement.setLong(2, record.getUserId());
            statement.setString(3, record.getLoginType());
            statement.setString(4, record.getUsername());
            statement.setString(5, record.getReason());
            statement.setString(6, record.getStatus());
            statement.setLong(7, record.getRequestedAt());
            statement.setLong(8, record.getCoolDownUntil());
            statement.setLong(9, record.getUserId());
            statement.setString(10, AccountClosureRecord.STATUS_PENDING);
            return statement;
        }, keyHolder);
        if (inserted == 0) {
            return Optional.empty();
        }
        Number key = keyHolder.getKey();
        if (key != null) {
            record.setId(key.longValue());
        }
        return Optional.of(record);
    }

    public Optional<AccountClosureRecord> findPending(long userId) {
        List<AccountClosureRecord> records = jdbcTemplate.query(
                "SELECT * FROM " + TABLE_NAME + " WHERE user_id = ? AND status = ? ORDER BY id DESC",
                MAPPER, userId, AccountClosureRecord.STATUS_PENDING);
        return records.stream().findFirst();
    }

    public Optional<AccountClosureRecord> findLatest(long userId) {
        List<AccountClosureRecord> records = jdbcTemplate.query(
                "SELECT * FROM " + TABLE_NAME + " WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                MAPPER, userId);
        return records.stream().findFirst();
    }

    public List<AccountClosureRecord> findDue(long now, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM " + TABLE_NAME + " WHERE status = ? AND cool_down_until <= ? ORDER BY id ASC LIMIT "
                        + Math.max(1, limit),
                MAPPER, AccountClosureRecord.STATUS_PENDING, now);
    }

    public int markRetry(long id, int attemptCount, long nextAttemptAt, String error) {
        return jdbcTemplate.update("UPDATE " + TABLE_NAME
                        + " SET attempt_count = ?, cool_down_until = ?, execute_error = ? WHERE id = ? AND status = ?",
                attemptCount, nextAttemptAt, error, id, AccountClosureRecord.STATUS_PENDING);
    }

    public int markFailed(long id, int attemptCount, String error) {
        return jdbcTemplate.update("UPDATE " + TABLE_NAME
                        + " SET attempt_count = ?, status = ?, executed_at = ?, execute_error = ? WHERE id = ?",
                attemptCount, AccountClosureRecord.STATUS_FAILED, System.currentTimeMillis(), error, id);
    }

    public int updateStatus(long id, String status, Long executedAt, String error) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE_NAME + " SET status = ?, executed_at = ?, execute_error = ? WHERE id = ?",
                status, executedAt, error, id);
    }
}
