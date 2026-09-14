package cn.slainc.oauth2.client;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class JdbcClientRecordRepository implements ClientRecordRepository {

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z0-9_.]+");

    private final JdbcTemplate jdbcTemplate;

    private final String tableName;

    private final boolean autoCreateTable;

    private volatile boolean schemaReady;

    private final RowMapper<Oauth2ClientRecord> mapper = (rs, rowNum) -> {
        Oauth2ClientRecord record = new Oauth2ClientRecord();
        record.setId(rs.getLong("id"));
        record.setClientId(rs.getString("client_id"));
        record.setClientSecret(rs.getString("client_secret"));
        record.setPreviousSecret(rs.getString("previous_secret"));
        long expireAt = rs.getLong("previous_secret_expire");
        if (!rs.wasNull()) {
            record.setPreviousSecretExpireAt(expireAt);
        }
        record.setClientName(rs.getString("client_name"));
        record.setContractScopes(split(rs.getString("contract_scopes")));
        record.setAllowRedirectUris(split(rs.getString("allow_redirect_uris")));
        record.setAllowGrantTypes(split(rs.getString("allow_grant_types")));
        record.setSubjectId(rs.getString("subject_id"));
        record.setAccessTokenTimeout(rs.getLong("access_token_timeout"));
        record.setRefreshTokenTimeout(rs.getLong("refresh_token_timeout"));
        record.setClientTokenTimeout(rs.getLong("client_token_timeout"));
        record.setMaxAccessTokenCount(rs.getInt("max_access_token_count"));
        record.setMaxRefreshTokenCount(rs.getInt("max_refresh_token_count"));
        record.setMaxClientTokenCount(rs.getInt("max_client_token_count"));
        record.setNewRefresh(rs.getBoolean("is_new_refresh"));
        record.setAutoConfirm(rs.getBoolean("is_auto_confirm"));
        record.setIpWhitelist(split(rs.getString("ip_whitelist")));
        record.setQpsLimit(rs.getLong("qps_limit"));
        record.setStatus(rs.getString("status"));
        record.setRemark(rs.getString("remark"));
        record.setCreatedAt(rs.getLong("created_at"));
        record.setUpdatedAt(rs.getLong("updated_at"));
        return record;
    };

    public JdbcClientRecordRepository(JdbcTemplate jdbcTemplate, String tableName, boolean autoCreateTable) {
        if (tableName == null || !TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法的表名：" + tableName);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.autoCreateTable = autoCreateTable;
    }

    @Override
    public Optional<Oauth2ClientRecord> find(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        ensureReady();
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM " + tableName + " WHERE client_id = ?", mapper, clientId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Oauth2ClientRecord> list() {
        ensureReady();
        return jdbcTemplate.query("SELECT * FROM " + tableName + " ORDER BY id", mapper);
    }

    @Override
    public Oauth2ClientRecord insert(Oauth2ClientRecord record) {
        ensureReady();
        long now = System.currentTimeMillis();
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO " + tableName + " (client_id, client_secret, previous_secret, previous_secret_expire,"
                            + " client_name, contract_scopes, allow_redirect_uris, allow_grant_types, subject_id,"
                            + " access_token_timeout, refresh_token_timeout, client_token_timeout,"
                            + " max_access_token_count, max_refresh_token_count, max_client_token_count,"
                            + " is_new_refresh, is_auto_confirm, ip_whitelist, qps_limit, status, remark, created_at, updated_at)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, record.getClientId());
            ps.setString(2, record.getClientSecret());
            ps.setString(3, record.getPreviousSecret());
            setNullableLong(ps, 4, record.getPreviousSecretExpireAt());
            ps.setString(5, record.getClientName());
            ps.setString(6, join(record.getContractScopes()));
            ps.setString(7, join(record.getAllowRedirectUris()));
            ps.setString(8, join(record.getAllowGrantTypes()));
            ps.setString(9, record.getSubjectId());
            ps.setLong(10, record.getAccessTokenTimeout());
            ps.setLong(11, record.getRefreshTokenTimeout());
            ps.setLong(12, record.getClientTokenTimeout());
            ps.setInt(13, record.getMaxAccessTokenCount());
            ps.setInt(14, record.getMaxRefreshTokenCount());
            ps.setInt(15, record.getMaxClientTokenCount());
            ps.setBoolean(16, record.isNewRefresh());
            ps.setBoolean(17, record.isAutoConfirm());
            ps.setString(18, join(record.getIpWhitelist()));
            ps.setLong(19, record.getQpsLimit());
            ps.setString(20, record.getStatus());
            ps.setString(21, record.getRemark());
            ps.setLong(22, record.getCreatedAt());
            ps.setLong(23, record.getUpdatedAt());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            record.setId(key.longValue());
        }
        return record;
    }

    @Override
    public Oauth2ClientRecord update(Oauth2ClientRecord record) {
        ensureReady();
        record.setUpdatedAt(System.currentTimeMillis());
        jdbcTemplate.update(
                "UPDATE " + tableName + " SET client_secret = ?, previous_secret = ?, previous_secret_expire = ?,"
                        + " client_name = ?, contract_scopes = ?, allow_redirect_uris = ?, allow_grant_types = ?,"
                        + " subject_id = ?, access_token_timeout = ?, refresh_token_timeout = ?, client_token_timeout = ?,"
                        + " max_access_token_count = ?, max_refresh_token_count = ?, max_client_token_count = ?,"
                        + " is_new_refresh = ?, is_auto_confirm = ?, ip_whitelist = ?, qps_limit = ?, status = ?,"
                        + " remark = ?, updated_at = ? WHERE client_id = ?",
                record.getClientSecret(), record.getPreviousSecret(), record.getPreviousSecretExpireAt(),
                record.getClientName(), join(record.getContractScopes()), join(record.getAllowRedirectUris()),
                join(record.getAllowGrantTypes()), record.getSubjectId(), record.getAccessTokenTimeout(),
                record.getRefreshTokenTimeout(), record.getClientTokenTimeout(), record.getMaxAccessTokenCount(),
                record.getMaxRefreshTokenCount(), record.getMaxClientTokenCount(), record.isNewRefresh(),
                record.isAutoConfirm(), join(record.getIpWhitelist()), record.getQpsLimit(), record.getStatus(),
                record.getRemark(), record.getUpdatedAt(), record.getClientId());
        return record;
    }

    @Override
    public void delete(String clientId) {
        ensureReady();
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE client_id = ?", clientId);
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
                    + "client_id VARCHAR(64) NOT NULL UNIQUE,"
                    + "client_secret VARCHAR(512) NOT NULL,"
                    + "previous_secret VARCHAR(512),"
                    + "previous_secret_expire BIGINT,"
                    + "client_name VARCHAR(128),"
                    + "contract_scopes VARCHAR(1024),"
                    + "allow_redirect_uris VARCHAR(2048),"
                    + "allow_grant_types VARCHAR(256),"
                    + "subject_id VARCHAR(64),"
                    + "access_token_timeout BIGINT NOT NULL DEFAULT 0,"
                    + "refresh_token_timeout BIGINT NOT NULL DEFAULT 0,"
                    + "client_token_timeout BIGINT NOT NULL DEFAULT 0,"
                    + "max_access_token_count INT NOT NULL DEFAULT 0,"
                    + "max_refresh_token_count INT NOT NULL DEFAULT 0,"
                    + "max_client_token_count INT NOT NULL DEFAULT 0,"
                    + "is_new_refresh TINYINT NOT NULL DEFAULT 1,"
                    + "is_auto_confirm TINYINT NOT NULL DEFAULT 0,"
                    + "ip_whitelist VARCHAR(1024),"
                    + "qps_limit BIGINT NOT NULL DEFAULT 0,"
                    + "status VARCHAR(16) NOT NULL DEFAULT 'enabled',"
                    + "remark VARCHAR(256),"
                    + "created_at BIGINT NOT NULL,"
                    + "updated_at BIGINT NOT NULL)");
            schemaReady = true;
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(",", values);
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
