package cn.slainc.sso.client;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class JdbcSsoClientRecordRepository implements SsoClientRecordRepository {

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z0-9_.]+");

    private final JdbcTemplate jdbcTemplate;

    private final String tableName;

    private final boolean autoCreateTable;

    private volatile boolean schemaReady;

    private final RowMapper<SsoClientRecord> mapper = (rs, rowNum) -> {
        SsoClientRecord record = new SsoClientRecord();
        record.setId(rs.getLong("id"));
        record.setClient(rs.getString("client"));
        record.setClientName(rs.getString("client_name"));
        record.setSecretKey(rs.getString("secret_key"));
        record.setAllowUrl(rs.getString("allow_url"));
        record.setBaseUrl(rs.getString("base_url"));
        record.setPushUrl(rs.getString("push_url"));
        record.setPush(rs.getBoolean("is_push"));
        record.setSlo(rs.getBoolean("is_slo"));
        record.setStatus(rs.getString("status"));
        record.setCreatedAt(rs.getLong("created_at"));
        record.setUpdatedAt(rs.getLong("updated_at"));
        return record;
    };

    public JdbcSsoClientRecordRepository(JdbcTemplate jdbcTemplate, String tableName, boolean autoCreateTable) {
        if (tableName == null || !TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法的表名：" + tableName);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
        this.autoCreateTable = autoCreateTable;
    }

    @Override
    public Optional<SsoClientRecord> find(String client) {
        if (client == null || client.isBlank()) {
            return Optional.empty();
        }
        ensureReady();
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT * FROM " + tableName + " WHERE client = ?", mapper, client));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<SsoClientRecord> list() {
        ensureReady();
        return jdbcTemplate.query("SELECT * FROM " + tableName + " ORDER BY id", mapper);
    }

    @Override
    public SsoClientRecord insert(SsoClientRecord record) {
        ensureReady();
        long now = System.currentTimeMillis();
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO " + tableName + " (client, client_name, secret_key, allow_url, base_url,"
                            + " push_url, is_push, is_slo, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, record.getClient());
            ps.setString(2, record.getClientName());
            ps.setString(3, record.getSecretKey());
            ps.setString(4, record.getAllowUrl());
            ps.setString(5, record.getBaseUrl());
            ps.setString(6, record.getPushUrl());
            ps.setBoolean(7, record.isPush());
            ps.setBoolean(8, record.isSlo());
            ps.setString(9, record.getStatus());
            ps.setLong(10, record.getCreatedAt());
            ps.setLong(11, record.getUpdatedAt());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            record.setId(key.longValue());
        }
        return record;
    }

    @Override
    public SsoClientRecord update(SsoClientRecord record) {
        ensureReady();
        record.setUpdatedAt(System.currentTimeMillis());
        jdbcTemplate.update(
                "UPDATE " + tableName + " SET client_name = ?, secret_key = ?, allow_url = ?, base_url = ?,"
                        + " push_url = ?, is_push = ?, is_slo = ?, status = ?, updated_at = ? WHERE client = ?",
                record.getClientName(), record.getSecretKey(), record.getAllowUrl(), record.getBaseUrl(),
                record.getPushUrl(), record.isPush(), record.isSlo(), record.getStatus(),
                record.getUpdatedAt(), record.getClient());
        return record;
    }

    @Override
    public void delete(String client) {
        ensureReady();
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE client = ?", client);
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
                    + "client VARCHAR(64) NOT NULL UNIQUE,"
                    + "client_name VARCHAR(128),"
                    + "secret_key VARCHAR(256),"
                    + "allow_url VARCHAR(2048),"
                    + "base_url VARCHAR(256),"
                    + "push_url VARCHAR(128),"
                    + "is_push TINYINT NOT NULL DEFAULT 1,"
                    + "is_slo TINYINT NOT NULL DEFAULT 1,"
                    + "status VARCHAR(16) NOT NULL DEFAULT 'enabled',"
                    + "created_at BIGINT NOT NULL,"
                    + "updated_at BIGINT NOT NULL)");
            schemaReady = true;
        }
    }
}
