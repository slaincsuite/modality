package cn.slainc.modules.auth.store;

import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.passkey.PasskeyRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

public class PasskeyStore {

    private static final String TABLE_NAME = AuthKeys.PASSKEY_TABLE;

    private static final RowMapper<PasskeyRecord> MAPPER = (rs, rowNum) -> {
        PasskeyRecord record = new PasskeyRecord();
        record.setId(rs.getLong("id"));
        long tenantId = rs.getLong("tenant_id");
        record.setTenantId(rs.wasNull() ? null : tenantId);
        record.setUserId(rs.getLong("user_id"));
        record.setLoginType(rs.getString("login_type"));
        record.setCredentialId(rs.getString("credential_id"));
        record.setName(rs.getString("name"));
        record.setPublicKey(rs.getString("public_key"));
        record.setCreatedAt(rs.getLong("created_at"));
        long lastUsedAt = rs.getLong("last_used_at");
        record.setLastUsedAt(rs.wasNull() ? null : lastUsedAt);
        return record;
    };

    private final JdbcTemplate jdbcTemplate;

    public PasskeyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PasskeyRecord> listByUser(Long userId) {
        return jdbcTemplate.query("SELECT * FROM " + TABLE_NAME
                        + " WHERE user_id = ? AND deleted = 0 ORDER BY created_at DESC",
                MAPPER, userId);
    }

    public PasskeyRecord findByCredentialId(String credentialId) {
        List<PasskeyRecord> records = jdbcTemplate.query("SELECT * FROM " + TABLE_NAME
                + " WHERE credential_id = ? AND deleted = 0", MAPPER, credentialId);
        return records.isEmpty() ? null : records.get(0);
    }

    public PasskeyRecord save(PasskeyRecord record) {
        long now = System.currentTimeMillis();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO " + TABLE_NAME
                            + " (tenant_id, user_id, login_type, credential_id, name, public_key, created_at,"
                            + " last_used_at, deleted, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, record.getTenantId());
            statement.setLong(2, record.getUserId());
            statement.setString(3, record.getLoginType());
            statement.setString(4, record.getCredentialId());
            statement.setString(5, record.getName());
            statement.setString(6, record.getPublicKey());
            statement.setLong(7, record.getCreatedAt());
            if (record.getLastUsedAt() == null) {
                statement.setNull(8, java.sql.Types.BIGINT);
            } else {
                statement.setLong(8, record.getLastUsedAt());
            }
            statement.setBoolean(9, false);
            statement.setLong(10, now);
            statement.setLong(11, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            record.setId(key.longValue());
        }
        return record;
    }

    public boolean delete(Long userId, String credentialId) {
        return jdbcTemplate.update("UPDATE " + TABLE_NAME
                        + " SET deleted = 1, update_time = ? WHERE user_id = ? AND credential_id = ? AND deleted = 0",
                System.currentTimeMillis(), userId, credentialId) > 0;
    }
}
