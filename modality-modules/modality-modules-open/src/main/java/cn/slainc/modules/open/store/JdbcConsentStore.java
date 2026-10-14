package cn.slainc.modules.open.store;

import cn.slainc.modules.open.OpenKeys;
import cn.slainc.openapi.domain.ConsentRecord;
import cn.slainc.openapi.spi.ConsentStore;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class JdbcConsentStore implements ConsentStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcConsentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ConsentRecord save(ConsentRecord record) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + OpenKeys.CONSENT_TABLE + " WHERE id = ?", Long.class, record.getId());
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE " + OpenKeys.CONSENT_TABLE
                            + " SET scopes = ?, granted_at = ?, revoked_at = ?, source = ? WHERE id = ?",
                    JsonUtils.toJson(record.getScopes()), record.getGrantedAt(), record.getRevokedAt(),
                    record.getSource(), record.getId());
            return record;
        }
        jdbcTemplate.update("INSERT INTO " + OpenKeys.CONSENT_TABLE
                        + " (id, user_id, app_id, scopes, granted_at, revoked_at, source)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                record.getId(), record.getUserId(), record.getAppId(), JsonUtils.toJson(record.getScopes()),
                record.getGrantedAt(), record.getRevokedAt(), record.getSource());
        return record;
    }

    @Override
    public Optional<ConsentRecord> find(String userId, String appId) {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.CONSENT_TABLE
                        + " WHERE user_id = ? AND app_id = ? ORDER BY granted_at DESC",
                JdbcConsentStore::mapRow, userId, appId).stream().findFirst();
    }

    @Override
    public List<ConsentRecord> listByUser(String userId) {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.CONSENT_TABLE
                + " WHERE user_id = ? ORDER BY granted_at DESC", JdbcConsentStore::mapRow, userId);
    }

    @Override
    public List<ConsentRecord> listByApp(String appId) {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.CONSENT_TABLE
                + " WHERE app_id = ? ORDER BY granted_at DESC", JdbcConsentStore::mapRow, appId);
    }

    @Override
    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM " + OpenKeys.CONSENT_TABLE + " WHERE id = ?", id);
    }

    private static ConsentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        ConsentRecord record = new ConsentRecord();
        record.setId(rs.getString("id"));
        record.setUserId(rs.getString("user_id"));
        record.setAppId(rs.getString("app_id"));
        List<String> scopes = JsonUtils.parseList(rs.getString("scopes"), String.class);
        record.setScopes(scopes == null ? List.of() : scopes);
        record.setGrantedAt(rs.getLong("granted_at"));
        long revoked = rs.getLong("revoked_at");
        record.setRevokedAt(rs.wasNull() ? null : revoked);
        record.setSource(rs.getString("source"));
        return record;
    }
}
