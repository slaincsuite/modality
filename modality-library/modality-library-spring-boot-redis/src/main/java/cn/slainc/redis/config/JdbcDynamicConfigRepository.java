package cn.slainc.redis.config;

import cn.slainc.core.config.DynamicConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class JdbcDynamicConfigRepository implements DynamicConfigRepository {

    private static final AtomicLong ID_SEQUENCE = new AtomicLong();

    private final JdbcTemplate jdbcTemplate;

    private final String table;

    private final RowMapper<DynamicConfig> rowMapper = this::mapRow;

    public JdbcDynamicConfigRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, DynamicConfigSchema.DEFAULT_TABLE);
    }

    public JdbcDynamicConfigRepository(JdbcTemplate jdbcTemplate, String table) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("JdbcTemplate 不能为空");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.table = DynamicConfigSchema.requireValidTable(table);
    }

    public String getTable() {
        return table;
    }

    public void initSchema() {
        jdbcTemplate.execute(DynamicConfigSchema.createTableSql(table));
    }

    @Override
    public List<DynamicConfig> findAll() {
        return jdbcTemplate.query(DynamicConfigSchema.selectAllSql(table), rowMapper);
    }

    @Override
    public Optional<DynamicConfig> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        List<DynamicConfig> list = jdbcTemplate.query(DynamicConfigSchema.selectByKeySql(table), rowMapper, key);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<DynamicConfig> findByGroup(String group) {
        return jdbcTemplate.query(DynamicConfigSchema.selectByGroupSql(table), rowMapper, group);
    }

    @Override
    public void save(DynamicConfig config) {
        if (config == null || config.getConfigKey() == null) {
            throw new IllegalArgumentException("动态配置的 configKey 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        if (config.getUpdatedAt() == null) {
            config.setUpdatedAt(now);
        }
        if (config.getCreatedAt() == null) {
            config.setCreatedAt(config.getUpdatedAt());
        }
        if (config.getDeleted() == null) {
            config.setDeleted(0);
        }
        Long count = jdbcTemplate.queryForObject(DynamicConfigSchema.countByKeySql(table), Long.class,
                config.getConfigKey());
        if (count != null && count > 0) {
            jdbcTemplate.update(DynamicConfigSchema.updateSql(table),
                    config.getConfigValue(),
                    config.getConfigGroup(),
                    config.getDescription(),
                    toInt(config.getEncrypted()),
                    toInt(config.getEnabled() == null ? Boolean.TRUE : config.getEnabled()),
                    Timestamp.valueOf(config.getUpdatedAt()),
                    config.getUpdatedBy(),
                    config.getVersion() == null ? 0 : config.getVersion(),
                    config.getTenantId() == null ? 1L : config.getTenantId(),
                    config.getConfigKey());
            return;
        }
        if (config.getId() == null) {
            config.setId(nextId());
        }
        jdbcTemplate.update(DynamicConfigSchema.insertSql(table),
                config.getId(),
                config.getConfigKey(),
                config.getConfigValue(),
                config.getConfigGroup(),
                config.getDescription(),
                toInt(config.getEncrypted()),
                toInt(config.getEnabled() == null ? Boolean.TRUE : config.getEnabled()),
                Timestamp.valueOf(config.getCreatedAt()),
                Timestamp.valueOf(config.getUpdatedAt()),
                config.getCreatedBy(),
                config.getUpdatedBy(),
                config.getDeleted() == null ? 0 : config.getDeleted(),
                config.getVersion() == null ? 0 : config.getVersion(),
                config.getTenantId() == null ? 1L : config.getTenantId());
    }

    @Override
    public void delete(String key) {
        if (key == null) {
            return;
        }
        jdbcTemplate.update(DynamicConfigSchema.deleteSql(table), Timestamp.valueOf(LocalDateTime.now()), key);
    }

    private DynamicConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
        DynamicConfig config = new DynamicConfig();
        config.setId(rs.getLong("id"));
        config.setConfigKey(rs.getString("config_key"));
        config.setConfigValue(rs.getString("config_value"));
        config.setConfigGroup(rs.getString("config_group"));
        config.setDescription(rs.getString("description"));
        config.setEncrypted(rs.getBoolean("encrypted"));
        config.setEnabled(rs.getBoolean("enabled"));
        config.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        config.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        config.setCreatedBy(rs.getString("created_by"));
        config.setUpdatedBy(rs.getString("updated_by"));
        config.setDeleted(rs.getInt("deleted"));
        config.setVersion(rs.getInt("version"));
        config.setTenantId(rs.getLong("tenant_id"));
        return config;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static int toInt(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private static long nextId() {
        return System.currentTimeMillis() * 1000L + Math.floorMod(ID_SEQUENCE.incrementAndGet(), 1000L);
    }
}
