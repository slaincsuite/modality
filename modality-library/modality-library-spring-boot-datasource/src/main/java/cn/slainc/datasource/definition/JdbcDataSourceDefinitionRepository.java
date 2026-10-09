package cn.slainc.datasource.definition;

import cn.slainc.datasource.enums.DataSourceStatus;
import cn.slainc.datasource.enums.DatabaseType;
import cn.slainc.datasource.enums.PoolType;
import cn.slainc.datasource.support.DataSourceIds;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class JdbcDataSourceDefinitionRepository implements DataSourceDefinitionRepository {

    private static final String COLUMNS = "id, name, group_name, database_type, driver_class_name, url, username,"
            + " password, status, weight, primary_flag, health_check_enabled, failover_target, description,"
            + " minimum_idle, maximum_pool_size, connection_timeout, idle_timeout, max_lifetime,"
            + " leak_detection_threshold, created_at, updated_at, created_by, updated_by, deleted, version, tenant_id,"
            + " pool_type, xa_data_source_class_name";

    private static final String DDL = "CREATE TABLE IF NOT EXISTS t_data_source ("
            + " id BIGINT NOT NULL PRIMARY KEY,"
            + " name VARCHAR(128) NOT NULL,"
            + " group_name VARCHAR(128),"
            + " database_type VARCHAR(32),"
            + " driver_class_name VARCHAR(255),"
            + " url VARCHAR(1024),"
            + " username VARCHAR(128),"
            + " password VARCHAR(1024),"
            + " status VARCHAR(32) DEFAULT 'ENABLED',"
            + " weight INT DEFAULT 1,"
            + " primary_flag TINYINT DEFAULT 0,"
            + " health_check_enabled TINYINT DEFAULT 1,"
            + " failover_target VARCHAR(128),"
            + " description VARCHAR(512),"
            + " minimum_idle INT,"
            + " maximum_pool_size INT,"
            + " connection_timeout BIGINT,"
            + " idle_timeout BIGINT,"
            + " max_lifetime BIGINT,"
            + " leak_detection_threshold BIGINT,"
            + " created_at DATETIME,"
            + " updated_at DATETIME,"
            + " created_by VARCHAR(64),"
            + " updated_by VARCHAR(64),"
            + " deleted TINYINT DEFAULT 0,"
            + " version INT DEFAULT 0,"
            + " tenant_id BIGINT,"
            + " pool_type VARCHAR(32),"
            + " xa_data_source_class_name VARCHAR(255),"
            + " CONSTRAINT uk_data_source_name UNIQUE (name))";

    private static final String TABLE_NAME = "t_data_source";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<DataSourceDefinition> rowMapper = (resultSet, rowNum) -> mapRow(resultSet);

    public JdbcDataSourceDefinitionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initialize() {
        jdbcTemplate.execute(DDL);
    }

    @Override
    public List<DataSourceDefinition> findAll() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM " + TABLE_NAME + " WHERE deleted = 0", rowMapper);
    }

    @Override
    public List<DataSourceDefinition> findEnabled() {
        return jdbcTemplate.query("SELECT " + COLUMNS + " FROM " + TABLE_NAME
                + " WHERE deleted = 0 AND status IN ('ENABLED', 'DEGRADED')", rowMapper);
    }

    @Override
    public Optional<DataSourceDefinition> findByName(String name) {
        List<DataSourceDefinition> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE_NAME + " WHERE deleted = 0 AND name = ?", rowMapper, name);
        return list.stream().findFirst();
    }

    @Override
    public DataSourceDefinition save(DataSourceDefinition definition) {
        if (definition.getId() == null) {
            definition.setId(DataSourceIds.nextId());
        }
        LocalDateTime now = LocalDateTime.now();
        definition.setCreatedAt(now);
        definition.setUpdatedAt(now);
        jdbcTemplate.update("INSERT INTO " + TABLE_NAME + " (" + COLUMNS + ")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                definition.getId(), definition.getName(), definition.getGroupName(),
                name(definition.getDatabaseType()), definition.getDriverClassName(), definition.getUrl(),
                definition.getUsername(), definition.getPassword(), name(definition.getStatus()),
                definition.getWeight(), flag(definition.getPrimaryFlag()),
                flag(definition.getHealthCheckEnabled()), definition.getFailoverTarget(),
                definition.getDescription(), definition.getMinimumIdle(), definition.getMaximumPoolSize(),
                definition.getConnectionTimeout(), definition.getIdleTimeout(), definition.getMaxLifetime(),
                definition.getLeakDetectionThreshold(), definition.getCreatedAt(), definition.getUpdatedAt(),
                definition.getCreatedBy(), definition.getUpdatedBy(), definition.getDeleted(),
                definition.getVersion(), definition.getTenantId(),
                name(definition.getPoolType()), definition.getXaDataSourceClassName());
        return definition;
    }

    @Override
    public DataSourceDefinition update(DataSourceDefinition definition) {
        definition.setUpdatedAt(LocalDateTime.now());
        jdbcTemplate.update("UPDATE " + TABLE_NAME + " SET group_name = ?, database_type = ?, driver_class_name = ?,"
                        + " url = ?, username = ?, password = ?, status = ?, weight = ?, primary_flag = ?,"
                        + " health_check_enabled = ?, failover_target = ?, description = ?, minimum_idle = ?,"
                        + " maximum_pool_size = ?, connection_timeout = ?, idle_timeout = ?, max_lifetime = ?,"
                        + " leak_detection_threshold = ?, updated_at = ?, updated_by = ?, version = version + 1,"
                        + " pool_type = ?, xa_data_source_class_name = ?"
                        + " WHERE name = ?",
                definition.getGroupName(), name(definition.getDatabaseType()), definition.getDriverClassName(),
                definition.getUrl(), definition.getUsername(), definition.getPassword(),
                name(definition.getStatus()), definition.getWeight(), flag(definition.getPrimaryFlag()),
                flag(definition.getHealthCheckEnabled()), definition.getFailoverTarget(),
                definition.getDescription(), definition.getMinimumIdle(), definition.getMaximumPoolSize(),
                definition.getConnectionTimeout(), definition.getIdleTimeout(), definition.getMaxLifetime(),
                definition.getLeakDetectionThreshold(), definition.getUpdatedAt(), definition.getUpdatedBy(),
                name(definition.getPoolType()), definition.getXaDataSourceClassName(),
                definition.getName());
        return definition;
    }

    @Override
    public boolean deleteByName(String name) {
        return jdbcTemplate.update("DELETE FROM " + TABLE_NAME + " WHERE name = ?", name) > 0;
    }

    private DataSourceDefinition mapRow(ResultSet resultSet) throws SQLException {
        DataSourceDefinition definition = new DataSourceDefinition();
        definition.setId(resultSet.getLong("id"));
        definition.setName(resultSet.getString("name"));
        definition.setGroupName(resultSet.getString("group_name"));
        definition.setDatabaseType(parseDatabaseType(resultSet.getString("database_type")));
        definition.setDriverClassName(resultSet.getString("driver_class_name"));
        definition.setUrl(resultSet.getString("url"));
        definition.setUsername(resultSet.getString("username"));
        definition.setPassword(resultSet.getString("password"));
        definition.setStatus(parseStatus(resultSet.getString("status")));
        definition.setWeight(resultSet.getInt("weight"));
        definition.setPrimaryFlag(resultSet.getBoolean("primary_flag"));
        definition.setHealthCheckEnabled(resultSet.getBoolean("health_check_enabled"));
        definition.setFailoverTarget(resultSet.getString("failover_target"));
        definition.setDescription(resultSet.getString("description"));
        definition.setMinimumIdle(nullableInt(resultSet, "minimum_idle"));
        definition.setMaximumPoolSize(nullableInt(resultSet, "maximum_pool_size"));
        definition.setConnectionTimeout(nullableLong(resultSet, "connection_timeout"));
        definition.setIdleTimeout(nullableLong(resultSet, "idle_timeout"));
        definition.setMaxLifetime(nullableLong(resultSet, "max_lifetime"));
        definition.setLeakDetectionThreshold(nullableLong(resultSet, "leak_detection_threshold"));
        definition.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        definition.setUpdatedAt(resultSet.getObject("updated_at", LocalDateTime.class));
        definition.setCreatedBy(resultSet.getString("created_by"));
        definition.setUpdatedBy(resultSet.getString("updated_by"));
        definition.setDeleted(resultSet.getInt("deleted"));
        definition.setVersion(resultSet.getInt("version"));
        long tenantId = resultSet.getLong("tenant_id");
        definition.setTenantId(resultSet.wasNull() ? null : tenantId);
        definition.setPoolType(parsePoolType(resultSet.getString("pool_type")));
        definition.setXaDataSourceClassName(resultSet.getString("xa_data_source_class_name"));
        return definition;
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static Integer flag(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private static DatabaseType parseDatabaseType(String value) {
        return value == null || value.isBlank() ? null : DatabaseType.valueOf(value);
    }

    private static PoolType parsePoolType(String value) {
        return value == null || value.isBlank() ? null : PoolType.valueOf(value);
    }

    private static DataSourceStatus parseStatus(String value) {
        return value == null || value.isBlank() ? DataSourceStatus.ENABLED : DataSourceStatus.valueOf(value);
    }

    private static Integer nullableInt(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
