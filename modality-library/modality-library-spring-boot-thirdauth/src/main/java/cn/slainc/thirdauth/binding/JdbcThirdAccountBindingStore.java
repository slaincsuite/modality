package cn.slainc.thirdauth.binding;

import cn.slainc.thirdauth.ThirdPartyPlatform;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

public class JdbcThirdAccountBindingStore implements ThirdAccountBindingStore {

    private static final RowMapper<ThirdAccountBinding> ROW_MAPPER = JdbcThirdAccountBindingStore::map;

    private final JdbcTemplate jdbcTemplate;

    private final String table;

    private final boolean autoCreateTable;

    private volatile boolean ready;

    public JdbcThirdAccountBindingStore(JdbcTemplate jdbcTemplate, String table, boolean autoCreateTable) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = table;
        this.autoCreateTable = autoCreateTable;
    }

    @Override
    public ThirdAccountBinding save(ThirdAccountBinding binding) {
        ensureReady();
        long now = System.currentTimeMillis();
        if (binding.getId() == null) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO " + table
                                + " (platform, open_id, union_id, user_id, nickname, avatar, bound_at, last_login_at, status)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, binding.getPlatform().code());
                statement.setString(2, binding.getOpenId());
                statement.setString(3, binding.getUnionId());
                statement.setString(4, binding.getUserId());
                statement.setString(5, binding.getNickname());
                statement.setString(6, binding.getAvatar());
                statement.setLong(7, binding.getBoundAt() > 0 ? binding.getBoundAt() : now);
                if (binding.getLastLoginAt() == null) {
                    statement.setNull(8, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(8, binding.getLastLoginAt());
                }
                statement.setString(9, binding.getStatus());
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key != null) {
                binding.setId(key.longValue());
            }
            return binding;
        }
        jdbcTemplate.update("UPDATE " + table
                        + " SET union_id = ?, user_id = ?, nickname = ?, avatar = ?, last_login_at = ?, status = ?"
                        + " WHERE id = ?",
                binding.getUnionId(), binding.getUserId(), binding.getNickname(), binding.getAvatar(),
                binding.getLastLoginAt(), binding.getStatus(), binding.getId());
        return binding;
    }

    @Override
    public Optional<ThirdAccountBinding> findActive(ThirdPartyPlatform platform, String openId) {
        ensureReady();
        try {
            return Optional.of(jdbcTemplate.queryForObject(
                    "SELECT * FROM " + table + " WHERE platform = ? AND open_id = ? AND status = 'ACTIVE'",
                    ROW_MAPPER, platform.code(), openId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ThirdAccountBinding> findByUnionId(ThirdPartyPlatform platform, String unionId) {
        ensureReady();
        if (unionId == null || unionId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(jdbcTemplate.queryForObject(
                    "SELECT * FROM " + table + " WHERE platform = ? AND union_id = ? AND status = 'ACTIVE'",
                    ROW_MAPPER, platform.code(), unionId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<ThirdAccountBinding> listByUser(String userId) {
        ensureReady();
        return jdbcTemplate.query(
                "SELECT * FROM " + table + " WHERE user_id = ? AND status = 'ACTIVE' ORDER BY bound_at", ROW_MAPPER,
                userId);
    }

    @Override
    public List<ThirdAccountBinding> listAll() {
        ensureReady();
        return jdbcTemplate.query("SELECT * FROM " + table + " ORDER BY bound_at", ROW_MAPPER);
    }

    private void ensureReady() {
        if (ready || !autoCreateTable) {
            return;
        }
        synchronized (this) {
            if (ready) {
                return;
            }
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "platform VARCHAR(32) NOT NULL,"
                    + "open_id VARCHAR(128) NOT NULL,"
                    + "union_id VARCHAR(128),"
                    + "user_id VARCHAR(64) NOT NULL,"
                    + "nickname VARCHAR(128),"
                    + "avatar VARCHAR(512),"
                    + "bound_at BIGINT NOT NULL,"
                    + "last_login_at BIGINT,"
                    + "status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',"
                    + "UNIQUE KEY uk_third_binding (platform, open_id))");
            ready = true;
        }
    }

    private static ThirdAccountBinding map(ResultSet resultSet, int rowNumber) throws SQLException {
        ThirdAccountBinding binding = new ThirdAccountBinding();
        binding.setId(resultSet.getLong("id"));
        binding.setPlatform(resultSet.getString("platform"));
        binding.setOpenId(resultSet.getString("open_id"));
        binding.setUnionId(resultSet.getString("union_id"));
        binding.setUserId(resultSet.getString("user_id"));
        binding.setNickname(resultSet.getString("nickname"));
        binding.setAvatar(resultSet.getString("avatar"));
        binding.setBoundAt(resultSet.getLong("bound_at"));
        long lastLoginAt = resultSet.getLong("last_login_at");
        binding.setLastLoginAt(resultSet.wasNull() ? null : lastLoginAt);
        binding.setStatus(resultSet.getString("status"));
        return binding;
    }
}
