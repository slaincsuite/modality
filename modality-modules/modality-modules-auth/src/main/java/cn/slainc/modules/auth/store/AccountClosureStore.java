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
        long executedAt = rs.getLong("executed_at");
        record.setExecutedAt(rs.wasNull() ? null : executedAt);
        record.setExecuteError(rs.getString("execute_error"));
        return record;
    };

    public AccountClosureStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AccountClosureRecord insert(AccountClosureRecord record) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + TABLE_NAME + "(tenant_id, user_id, login_type, username, reason, status,"
                            + " requested_at, cool_down_until) VALUES (?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, record.getTenantId());
            statement.setLong(2, record.getUserId());
            statement.setString(3, record.getLoginType());
            statement.setString(4, record.getUsername());
            statement.setString(5, record.getReason());
            statement.setString(6, record.getStatus());
            statement.setLong(7, record.getRequestedAt());
            statement.setLong(8, record.getCoolDownUntil());
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            record.setId(key.longValue());
        }
        return record;
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

    public int updateStatus(long id, String status, Long executedAt, String error) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE_NAME + " SET status = ?, executed_at = ?, execute_error = ? WHERE id = ?",
                status, executedAt, error, id);
    }
}
