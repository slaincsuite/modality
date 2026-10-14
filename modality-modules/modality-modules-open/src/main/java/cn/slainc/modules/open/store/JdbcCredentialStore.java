package cn.slainc.modules.open.store;

import cn.slainc.modules.open.OpenKeys;
import cn.slainc.openapi.domain.AppCredential;
import cn.slainc.openapi.spi.CredentialStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

// 凭证表只存映射与状态：明文 secret 仅在签发/轮换响应一次性返回，落库一律掩码（真实凭证在 oauth2 / api-key 侧）
public class JdbcCredentialStore implements CredentialStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCredentialStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AppCredential save(AppCredential credential) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + OpenKeys.CREDENTIAL_TABLE + " WHERE app_id = ?", Long.class,
                credential.getAppId());
        String maskedSecret = OpenKeys.MASKED_SECRET;
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE " + OpenKeys.CREDENTIAL_TABLE
                            + " SET client_id = ?, client_secret = ?, type = ?, status = ?,"
                            + " previous_secret_expire_at = ?, issued_at = ?, rotated_at = ? WHERE app_id = ?",
                    credential.getClientId(), maskedSecret, credential.getType().name(),
                    credential.getStatus().name(), credential.getPreviousSecretExpireAt(),
                    credential.getIssuedAt(), credential.getRotatedAt(), credential.getAppId());
            return credential;
        }
        jdbcTemplate.update("INSERT INTO " + OpenKeys.CREDENTIAL_TABLE
                        + " (app_id, client_id, client_secret, type, status, previous_secret_expire_at,"
                        + " issued_at, rotated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                credential.getAppId(), credential.getClientId(), maskedSecret, credential.getType().name(),
                credential.getStatus().name(), credential.getPreviousSecretExpireAt(), credential.getIssuedAt(),
                credential.getRotatedAt());
        return credential;
    }

    @Override
    public Optional<AppCredential> find(String appId) {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.CREDENTIAL_TABLE + " WHERE app_id = ?",
                JdbcCredentialStore::mapRow, appId).stream().findFirst();
    }

    @Override
    public void delete(String appId) {
        jdbcTemplate.update("DELETE FROM " + OpenKeys.CREDENTIAL_TABLE + " WHERE app_id = ?", appId);
    }

    public List<AppCredential> listByType(AppCredential.Type type) {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.CREDENTIAL_TABLE + " WHERE type = ?",
                JdbcCredentialStore::mapRow, type.name());
    }

    private static AppCredential mapRow(ResultSet rs, int rowNum) throws SQLException {
        AppCredential credential = new AppCredential();
        credential.setAppId(rs.getString("app_id"));
        credential.setClientId(rs.getString("client_id"));
        credential.setClientSecret(OpenKeys.MASKED_SECRET);
        credential.setType(AppCredential.Type.valueOf(rs.getString("type")));
        credential.setStatus(AppCredential.Status.valueOf(rs.getString("status")));
        long previousExpireAt = rs.getLong("previous_secret_expire_at");
        credential.setPreviousSecretExpireAt(rs.wasNull() ? null : previousExpireAt);
        credential.setIssuedAt(rs.getLong("issued_at"));
        long rotatedAt = rs.getLong("rotated_at");
        credential.setRotatedAt(rs.wasNull() ? null : rotatedAt);
        return credential;
    }
}
