package cn.slainc.oauth2.consent;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class JdbcConsentRecordStore implements ConsentRecordStore {

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z0-9_.]+");

    private final JdbcTemplate jdbcTemplate;

    private final String tableName;

    private final boolean autoCreateTable;

    private volatile boolean schemaReady;

    private final RowMapper<ConsentRecord> mapper = (rs, rowNum) -> {
        ConsentRecord record = new ConsentRecord();
        record.setId(rs.getLong("id"));
        record.setLoginId(rs.getString("login_id"));
        record.setClientId(rs.getString("client_id"));
        record.setScopes(split(rs.getString("scopes")));
        record.setGrantedAt(rs.getLong("granted_at"));
        long revokedAt = rs.getLong("revoked_at");
        if (!rs.wasNull()) {
            record.setRevokedAt(revokedAt);
        }
        return record;
    };

    public JdbcConsentRecordStore(JdbcTemplate jdbcTemplate, String tableName, boolean autoCreateTable) {
        if (tableName == null || !TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法的表名：" + tableName);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.autoCreateTable = autoCreateTable;
    }

    @Override
    public void upsert(String loginId, String clientId, List<String> scopes, long grantedAt) {
        ensureReady();
        String scopeText = scopes == null ? "" : String.join(",", scopes);
        int updated = jdbcTemplate.update(
                "UPDATE " + tableName + " SET scopes = ?, granted_at = ?, revoked_at = NULL"
                        + " WHERE login_id = ? AND client_id = ?",
                scopeText, grantedAt, loginId, clientId);
        if (updated > 0) {
            return;
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO " + tableName + " (login_id, client_id, scopes, granted_at) VALUES (?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, loginId);
                ps.setString(2, clientId);
                ps.setString(3, scopeText);
                ps.setLong(4, grantedAt);
                return ps;
            }, keyHolder);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            jdbcTemplate.update(
                    "UPDATE " + tableName + " SET scopes = ?, granted_at = ?, revoked_at = NULL"
                            + " WHERE login_id = ? AND client_id = ?",
                    scopeText, grantedAt, loginId, clientId);
        }
    }

    @Override
    public void markRevoked(String loginId, String clientId, long revokedAt) {
        ensureReady();
        jdbcTemplate.update(
                "UPDATE " + tableName + " SET revoked_at = ? WHERE login_id = ? AND client_id = ? AND revoked_at IS NULL",
                revokedAt, loginId, clientId);
    }

    @Override
    public List<ConsentRecord> listByLoginId(String loginId) {
        ensureReady();
        return jdbcTemplate.query(
                "SELECT * FROM " + tableName + " WHERE login_id = ? ORDER BY granted_at DESC", mapper, loginId);
    }

    @Override
    public List<ConsentRecord> listByClientId(String clientId) {
        ensureReady();
        return jdbcTemplate.query(
                "SELECT * FROM " + tableName + " WHERE client_id = ? ORDER BY granted_at DESC", mapper, clientId);
    }

    @Override
    public Optional<ConsentRecord> find(String loginId, String clientId) {
        ensureReady();
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM " + tableName + " WHERE login_id = ? AND client_id = ?", mapper, loginId, clientId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private void ensureReady() {
        if (schemaReady || !autoCreateTable) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "login_id VARCHAR(64) NOT NULL,"
                    + "client_id VARCHAR(64) NOT NULL,"
                    + "scopes VARCHAR(1024),"
                    + "granted_at BIGINT NOT NULL,"
                    + "revoked_at BIGINT,"
                    + "CONSTRAINT uk_oauth2_consent UNIQUE (login_id, client_id))");
            schemaReady = true;
        }
    }

    private static List<String> split(String value) {
        List<String> result = new ArrayList<>();
        if (value != null && !value.isBlank()) {
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    result.add(part.trim());
                }
            }
        }
        return result;
    }
}
