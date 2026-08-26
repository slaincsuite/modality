package cn.slainc.tenant.isolation;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.datasource.enums.DatabaseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class SchemaTenantIsolationHandler implements TenantIsolationHandler {

    private static final Logger log = LoggerFactory.getLogger(SchemaTenantIsolationHandler.class);

    private static final Pattern SAFE_SCHEMA = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final String DEFAULT_PREFIX = "tenant_";

    private final JdbcTemplate jdbcTemplate;

    private final String schemaPrefix;

    public SchemaTenantIsolationHandler(JdbcTemplate jdbcTemplate, String schemaPrefix) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaPrefix = schemaPrefix == null || schemaPrefix.isBlank() ? DEFAULT_PREFIX : schemaPrefix.trim();
    }

    @Override
    public boolean supports(TenantIsolationStrategy strategy) {
        return strategy == TenantIsolationStrategy.SCHEMA;
    }

    @Override
    public void apply(TenantInfo tenant) {
        String schema = schemaName(tenant);
        tenant.setSchemaName(schema);
        tenant.putMetadata(TenantInfo.METADATA_SCHEMA_NAME, schema);
        if (jdbcTemplate == null) {
            log.warn("未配置 JdbcTemplate，跳过租户 [{}] 的 schema 创建：{}", tenant.getTenantId(), schema);
            return;
        }
        DatabaseType databaseType = databaseType();
        jdbcTemplate.execute(createSchemaSql(databaseType, schema));
        tenant.putMetadata(searchPathKey(databaseType), schema);
        log.info("租户 [{}] 的 schema [{}] 已就绪", tenant.getTenantId(), schema);
    }

    @Override
    public void remove(TenantInfo tenant) {
        String schema = schemaName(tenant);
        if (jdbcTemplate == null) {
            log.warn("未配置 JdbcTemplate，跳过租户 [{}] 的 schema 清理：{}", tenant.getTenantId(), schema);
            return;
        }
        jdbcTemplate.execute(dropSchemaSql(databaseType(), schema));
        log.info("租户 [{}] 的 schema [{}] 已清理", tenant.getTenantId(), schema);
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public String describe() {
        return "Schema 隔离：共享数据库实例，为每个租户执行 CREATE SCHEMA/DATABASE 并记录 search_path 或 currentSchema";
    }

    public String schemaName(TenantInfo tenant) {
        String schema = tenant.getSchemaName();
        if (schema == null || schema.isBlank()) {
            Object suffix = tenant.getTenantCode() == null || tenant.getTenantCode().isBlank()
                    ? tenant.getTenantId() : tenant.getTenantCode();
            schema = schemaPrefix + suffix;
        }
        if (!SAFE_SCHEMA.matcher(schema).matches()) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "非法的 schema 名称：" + schema);
        }
        return schema;
    }

    public String createSchemaSql(DatabaseType databaseType, String schema) {
        requireSupported(databaseType);
        if (isMysqlLike(databaseType)) {
            return "CREATE DATABASE IF NOT EXISTS " + schema;
        }
        return "CREATE SCHEMA IF NOT EXISTS " + schema;
    }

    public String dropSchemaSql(DatabaseType databaseType, String schema) {
        requireSupported(databaseType);
        if (isMysqlLike(databaseType)) {
            return "DROP DATABASE IF EXISTS " + schema;
        }
        return "DROP SCHEMA IF EXISTS " + schema + " CASCADE";
    }

    public String searchPathKey(DatabaseType databaseType) {
        return databaseType == DatabaseType.POSTGRE_SQL || databaseType == DatabaseType.KINGBASE
                ? TenantInfo.METADATA_SEARCH_PATH : TenantInfo.METADATA_CURRENT_SCHEMA;
    }

    private void requireSupported(DatabaseType databaseType) {
        if (databaseType == DatabaseType.ORACLE || databaseType == DatabaseType.SQL_SERVER
                || databaseType == DatabaseType.DM || databaseType == DatabaseType.SHENTONG) {
            throw new BizException("数据库类型 [" + databaseType.getDisplayName() + "] 暂不支持 Schema 隔离");
        }
    }

    private static boolean isMysqlLike(DatabaseType databaseType) {
        return databaseType == DatabaseType.MYSQL || databaseType == DatabaseType.TIDB
                || databaseType == DatabaseType.OCEAN_BASE;
    }

    private DatabaseType databaseType() {
        if (jdbcTemplate == null) {
            return null;
        }
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            return null;
        }
        try (Connection connection = dataSource.getConnection()) {
            return DatabaseType.fromUrl(connection.getMetaData().getURL());
        } catch (SQLException exception) {
            log.warn("读取数据库类型失败，按标准 SQL 处理：{}", exception.getMessage());
            return null;
        }
    }
}
