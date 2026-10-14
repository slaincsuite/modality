package cn.slainc.modules.auth.store;

import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.mfa.MfaFactorRecord;
import cn.slainc.mfa.spi.MfaFactorSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public class JdbcMfaFactorStore implements MfaFactorStore {

    private static final String TABLE_NAME = AuthKeys.MFA_FACTOR_TABLE;

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<MfaFactorRecord> MAPPER = (rs, rowNum) -> {
        MfaFactorRecord record = new MfaFactorRecord();
        record.setId(rs.getLong("id"));
        long tenantId = rs.getLong("tenant_id");
        record.setTenantId(rs.wasNull() ? null : tenantId);
        record.setUserId(rs.getLong("user_id"));
        record.setLoginType(rs.getString("login_type"));
        record.setUsername(rs.getString("username"));
        record.setFactorType(rs.getString("factor_type"));
        record.setSecretCipher(rs.getString("secret_cipher"));
        record.setSecretKeyId(rs.getString("secret_key_id"));
        record.setActivated(rs.getBoolean("activated"));
        record.setRecoveryCodes(rs.getString("recovery_codes"));
        long lastUsedAt = rs.getLong("last_used_at");
        record.setLastUsedAt(rs.wasNull() ? null : toDateTime(lastUsedAt));
        record.setFailCount(rs.getInt("fail_count"));
        long lockedUntil = rs.getLong("locked_until");
        record.setLockedUntil(rs.wasNull() ? null : toDateTime(lockedUntil));
        return record;
    };

    public JdbcMfaFactorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MfaFactorRecord findRecord(Long userId, String factorType) {
        if (userId == null || factorType == null) {
            return null;
        }
        List<MfaFactorRecord> records = jdbcTemplate.query(
                "SELECT * FROM " + TABLE_NAME + " WHERE user_id = ? AND factor_type = ?",
                MAPPER, userId, factorType);
        return records.isEmpty() ? null : records.get(0);
    }

    @Override
    public MfaFactorRecord saveRecord(MfaFactorRecord record) {
        if (record.getId() == null) {
            return insert(record);
        }
        jdbcTemplate.update("UPDATE " + TABLE_NAME
                        + " SET tenant_id = ?, login_type = ?, username = ?, secret_cipher = ?, secret_key_id = ?,"
                        + " activated = ?, recovery_codes = ?, last_used_at = ?, fail_count = ?, locked_until = ?,"
                        + " update_time = ? WHERE id = ?",
                record.getTenantId(), record.getLoginType(), record.getUsername(), record.getSecretCipher(),
                record.getSecretKeyId(), Boolean.TRUE.equals(record.getActivated()), record.getRecoveryCodes(),
                toMillis(record.getLastUsedAt()), failCountOf(record), toMillis(record.getLockedUntil()),
                System.currentTimeMillis(), record.getId());
        return record;
    }

    private MfaFactorRecord insert(MfaFactorRecord record) {
        long now = System.currentTimeMillis();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + TABLE_NAME + "(tenant_id, user_id, login_type, username, factor_type,"
                            + " secret_cipher, secret_key_id, activated, recovery_codes, last_used_at, fail_count,"
                            + " locked_until, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setObject(1, record.getTenantId());
            statement.setLong(2, record.getUserId());
            statement.setString(3, record.getLoginType());
            statement.setString(4, record.getUsername());
            statement.setString(5, record.getFactorType());
            statement.setString(6, record.getSecretCipher());
            statement.setString(7, record.getSecretKeyId());
            statement.setBoolean(8, Boolean.TRUE.equals(record.getActivated()));
            statement.setString(9, record.getRecoveryCodes());
            setNullableLong(statement, 10, toMillis(record.getLastUsedAt()));
            statement.setInt(11, failCountOf(record));
            setNullableLong(statement, 12, toMillis(record.getLockedUntil()));
            statement.setLong(13, now);
            statement.setLong(14, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            record.setId(key.longValue());
        }
        return record;
    }

    @Override
    public MfaFactorSnapshot find(Long userId, String factorType) {
        return toSnapshot(findRecord(userId, factorType));
    }

    @Override
    public Long insert(MfaFactorSnapshot factor) {
        MfaFactorRecord record = toRecord(factor);
        record.setId(null);
        return saveRecord(record).getId();
    }

    @Override
    public void updateActivation(Long userId, String factorType, boolean activated) {
        jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET activated = ?, update_time = ?"
                + " WHERE user_id = ? AND factor_type = ?", activated, System.currentTimeMillis(), userId, factorType);
    }

    @Override
    public void updateFailure(Long userId, String factorType, int failCount, LocalDateTime lockedUntil) {
        jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET fail_count = ?, locked_until = ?, update_time = ?"
                        + " WHERE user_id = ? AND factor_type = ?",
                failCount, toMillis(lockedUntil), System.currentTimeMillis(), userId, factorType);
    }

    @Override
    public void updateSuccess(Long userId, String factorType, LocalDateTime lastUsedAt) {
        jdbcTemplate.update("UPDATE " + TABLE_NAME
                        + " SET fail_count = 0, locked_until = NULL, last_used_at = ?, update_time = ?"
                        + " WHERE user_id = ? AND factor_type = ?",
                toMillis(lastUsedAt), System.currentTimeMillis(), userId, factorType);
    }

    @Override
    public void updateRecoveryCodes(Long userId, String factorType, String recoveryCodes) {
        jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET recovery_codes = ?, update_time = ?"
                + " WHERE user_id = ? AND factor_type = ?", recoveryCodes, System.currentTimeMillis(),
                userId, factorType);
    }

    @Override
    public boolean delete(Long userId, String factorType) {
        if (userId == null || factorType == null) {
            return false;
        }
        return jdbcTemplate.update("DELETE FROM " + TABLE_NAME + " WHERE user_id = ? AND factor_type = ?",
                userId, factorType) > 0;
    }

    private static int failCountOf(MfaFactorRecord record) {
        return record.getFailCount() == null ? 0 : record.getFailCount();
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static Long toMillis(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime toDateTime(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private static MfaFactorSnapshot toSnapshot(MfaFactorRecord record) {
        if (record == null) {
            return null;
        }
        return new MfaFactorSnapshot(record.getId(), record.getUserId(), record.getFactorType(),
                record.getSecretCipher(), record.getSecretKeyId(), record.getActivated(), record.getRecoveryCodes(),
                record.getLastUsedAt(), record.getFailCount(), record.getLockedUntil(), record.getTenantId());
    }

    private static MfaFactorRecord toRecord(MfaFactorSnapshot factor) {
        MfaFactorRecord record = new MfaFactorRecord();
        record.setId(factor.id());
        record.setUserId(factor.userId());
        record.setFactorType(factor.factorType());
        record.setSecretCipher(factor.secretCipher());
        record.setSecretKeyId(factor.secretKeyId());
        record.setActivated(factor.activated());
        record.setRecoveryCodes(factor.recoveryCodes());
        record.setLastUsedAt(factor.lastUsedAt());
        record.setFailCount(factor.failCount());
        record.setLockedUntil(factor.lockedUntil());
        record.setTenantId(factor.tenantId());
        return record;
    }
}
