package cn.slainc.tenant.isolation;

import cn.slainc.core.exception.BizException;
import cn.slainc.datasource.enums.DatabaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaTenantIsolationHandlerTest {

    @Test
    @DisplayName("只支持 Schema 隔离策略并给出中文说明")
    void supportsOnlySchema() {
        SchemaTenantIsolationHandler handler = new SchemaTenantIsolationHandler(null, "tenant_");
        assertTrue(handler.supports(TenantIsolationStrategy.SCHEMA));
        assertFalse(handler.supports(TenantIsolationStrategy.ROW));
        assertTrue(handler.describe().contains("CREATE SCHEMA"), handler.describe());
    }

    @Test
    @DisplayName("PostgreSQL 执行 CREATE SCHEMA 并写入 search_path 元数据")
    void createsSchemaOnPostgres() throws SQLException {
        JdbcTemplate jdbcTemplate = jdbcTemplate("jdbc:postgresql://127.0.0.1:5432/modality");
        SchemaTenantIsolationHandler handler = new SchemaTenantIsolationHandler(jdbcTemplate, "tenant_");
        TenantInfo tenant = TenantInfo.of(5L, "acme", TenantIsolationStrategy.SCHEMA);

        handler.apply(tenant);

        verify(jdbcTemplate).execute("CREATE SCHEMA IF NOT EXISTS tenant_acme");
        assertEquals("tenant_acme", tenant.getSchemaName());
        assertEquals("tenant_acme", tenant.getMetadata().get(TenantInfo.METADATA_SEARCH_PATH));
        assertEquals("tenant_acme", tenant.getMetadata().get(TenantInfo.METADATA_SCHEMA_NAME));
    }

    @Test
    @DisplayName("MySQL 执行 CREATE DATABASE 并写入 currentSchema 元数据")
    void createsDatabaseOnMysql() throws SQLException {
        JdbcTemplate jdbcTemplate = jdbcTemplate("jdbc:mysql://127.0.0.1:3306/modality");
        SchemaTenantIsolationHandler handler = new SchemaTenantIsolationHandler(jdbcTemplate, "tenant_");
        TenantInfo tenant = TenantInfo.of(5L, "acme", TenantIsolationStrategy.SCHEMA);

        handler.apply(tenant);

        verify(jdbcTemplate).execute("CREATE DATABASE IF NOT EXISTS tenant_acme");
        assertEquals("tenant_acme", tenant.getMetadata().get(TenantInfo.METADATA_CURRENT_SCHEMA));
        assertNull(tenant.getMetadata().get(TenantInfo.METADATA_SEARCH_PATH));
    }

    @Test
    @DisplayName("H2 执行 CREATE SCHEMA")
    void createsSchemaOnH2() throws SQLException {
        JdbcTemplate jdbcTemplate = jdbcTemplate("jdbc:h2:mem:tenant-schema;MODE=MySQL");
        SchemaTenantIsolationHandler handler = new SchemaTenantIsolationHandler(jdbcTemplate, "tenant_");
        TenantInfo tenant = TenantInfo.of(5L, "acme", TenantIsolationStrategy.SCHEMA);

        handler.apply(tenant);

        verify(jdbcTemplate).execute("CREATE SCHEMA IF NOT EXISTS tenant_acme");
    }

    @Test
    @DisplayName("删除时按数据库类型执行 DROP 语句")
    void dropsSchemaAndDatabase() throws SQLException {
        JdbcTemplate postgres = jdbcTemplate("jdbc:postgresql://127.0.0.1:5432/modality");
        SchemaTenantIsolationHandler handler = new SchemaTenantIsolationHandler(postgres, "tenant_");
        TenantInfo tenant = TenantInfo.of(5L, "acme", TenantIsolationStrategy.SCHEMA);
        tenant.setSchemaName("tenant_acme");
        handler.remove(tenant);
        verify(postgres).execute("DROP SCHEMA IF EXISTS tenant_acme CASCADE");

        JdbcTemplate mysql = jdbcTemplate("jdbc:mysql://127.0.0.1:3306/modality");
        new SchemaTenantIsolationHandler(mysql, "tenant_").remove(tenant);
        verify(mysql).execute("DROP DATABASE IF EXISTS tenant_acme");
    }

    @Test
    @DisplayName("未配置 JdbcTemplate 时安全跳过，但仍记录 schema 名称")
    void skipsSafelyWithoutJdbcTemplate() {
        SchemaTenantIsolationHandler handler = new SchemaTenantIsolationHandler(null, "tenant_");
        TenantInfo tenant = TenantInfo.of(5L, "acme", TenantIsolationStrategy.SCHEMA);
        assertDoesNotThrow(() -> handler.apply(tenant));
        assertEquals("tenant_acme", tenant.getSchemaName());
        assertDoesNotThrow(() -> handler.remove(tenant));
    }

    @Test
    @DisplayName("非法 schema 名称直接抛出中文业务异常")
    void rejectsIllegalSchemaName() {
        SchemaTenantIsolationHandler handler = new SchemaTenantIsolationHandler(null, "tenant_");
        TenantInfo tenant = TenantInfo.of(5L, "acme; DROP TABLE x", TenantIsolationStrategy.SCHEMA);
        BizException exception = assertThrows(BizException.class, () -> handler.apply(tenant));
        assertTrue(exception.getMessage().contains("非法的 schema 名称"), exception.getMessage());
    }

    @Test
    @DisplayName("缺少租户编码时用租户 ID 兜底，未支持的数据库类型拒绝")
    void usesTenantIdAndRejectsUnsupportedDatabase() throws SQLException {
        SchemaTenantIsolationHandler handler = new SchemaTenantIsolationHandler(null, "");
        assertEquals("tenant_9", handler.schemaName(TenantInfo.of(9L, null, TenantIsolationStrategy.SCHEMA)));
        assertThrows(BizException.class, () -> handler.createSchemaSql(DatabaseType.ORACLE, "tenant_9"));

        JdbcTemplate oracle = jdbcTemplate("jdbc:oracle:thin:@127.0.0.1:1521:orcl");
        SchemaTenantIsolationHandler oracleHandler = new SchemaTenantIsolationHandler(oracle, "tenant_");
        TenantInfo tenant = TenantInfo.of(5L, "acme", TenantIsolationStrategy.SCHEMA);
        assertThrows(BizException.class, () -> oracleHandler.apply(tenant));
        verify(oracle, never()).execute(anyString());
    }

    private JdbcTemplate jdbcTemplate(String url) throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getURL()).thenReturn(url);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        return jdbcTemplate;
    }
}
