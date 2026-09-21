package cn.slainc.redis.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicConfigSchemaTest {

    @Test
    void createTableContainsAllColumns() {
        String sql = DynamicConfigSchema.createTableSql("t_dynamic_config");
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS t_dynamic_config");
        assertThat(sql).contains("config_key VARCHAR(255) NOT NULL");
        assertThat(sql).contains("config_value TEXT");
        assertThat(sql).contains("config_group VARCHAR(128)");
        assertThat(sql).contains("encrypted TINYINT DEFAULT 0");
        assertThat(sql).contains("enabled TINYINT DEFAULT 1");
        assertThat(sql).contains("tenant_id BIGINT DEFAULT 1");
        assertThat(sql).contains("UNIQUE KEY uk_config_key (config_key)");
    }

    @Test
    void illegalTableNameRejectedDefaultApplies() {
        assertThat(DynamicConfigSchema.requireValidTable(null)).isEqualTo("t_dynamic_config");
        assertThat(DynamicConfigSchema.requireValidTable("  ")).isEqualTo("t_dynamic_config");
        assertThat(DynamicConfigSchema.requireValidTable(" t_config_2 ")).isEqualTo("t_config_2");
        assertThatThrownBy(() -> DynamicConfigSchema.requireValidTable("其他配置表"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法的动态配置表名");
        assertThatThrownBy(() -> DynamicConfigSchema.requireValidTable("t_config; DROP TABLE x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法的动态配置表名");
        assertThatThrownBy(() -> DynamicConfigSchema.createTableSql("1_bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statementsShareTableWithCorrectParams() {
        String table = "t_dynamic_config";
        assertThat(DynamicConfigSchema.selectAllSql(table)).endsWith("WHERE deleted = 0");
        assertThat(DynamicConfigSchema.selectByKeySql(table)).contains("config_key = ?");
        assertThat(DynamicConfigSchema.selectByGroupSql(table)).contains("config_group = ? ORDER BY config_key");
        assertThat(DynamicConfigSchema.insertSql(table)).contains("(?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
        assertThat(DynamicConfigSchema.updateSql(table)).contains("WHERE config_key = ?");
        assertThat(DynamicConfigSchema.deleteSql(table)).contains("SET deleted = 1");
        assertThat(DynamicConfigSchema.countByKeySql(table)).startsWith("SELECT COUNT(1) FROM t_dynamic_config");
    }
}
