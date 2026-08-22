package cn.slainc.redis.config;

import java.util.regex.Pattern;

public final class DynamicConfigSchema {

    public static final String DEFAULT_TABLE = "t_dynamic_config";

    public static final String COLUMNS = "id, config_key, config_value, config_group, description, encrypted, enabled,"
            + " created_at, updated_at, created_by, updated_by, deleted, version, tenant_id";

    private static final Pattern TABLE_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private DynamicConfigSchema() {
    }

    public static String requireValidTable(String table) {
        String name = table == null || table.isBlank() ? DEFAULT_TABLE : table.trim();
        if (!TABLE_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("非法的动态配置表名：" + table);
        }
        return name;
    }

    public static String createTableSql(String table) {
        String name = requireValidTable(table);
        return "CREATE TABLE IF NOT EXISTS " + name + " ("
                + "id BIGINT PRIMARY KEY,"
                + " config_key VARCHAR(255) NOT NULL,"
                + " config_value TEXT,"
                + " config_group VARCHAR(128),"
                + " description VARCHAR(512),"
                + " encrypted TINYINT DEFAULT 0,"
                + " enabled TINYINT DEFAULT 1,"
                + " created_at DATETIME,"
                + " updated_at DATETIME,"
                + " created_by VARCHAR(64),"
                + " updated_by VARCHAR(64),"
                + " deleted TINYINT DEFAULT 0,"
                + " version INT DEFAULT 0,"
                + " tenant_id BIGINT DEFAULT 1,"
                + " UNIQUE KEY uk_config_key (config_key))";
    }

    public static String selectAllSql(String table) {
        return "SELECT " + COLUMNS + " FROM " + requireValidTable(table) + " WHERE deleted = 0";
    }

    public static String selectByKeySql(String table) {
        return "SELECT " + COLUMNS + " FROM " + requireValidTable(table) + " WHERE deleted = 0 AND config_key = ?";
    }

    public static String selectByGroupSql(String table) {
        return "SELECT " + COLUMNS + " FROM " + requireValidTable(table)
                + " WHERE deleted = 0 AND config_group = ? ORDER BY config_key";
    }

    public static String countByKeySql(String table) {
        return "SELECT COUNT(1) FROM " + requireValidTable(table) + " WHERE config_key = ?";
    }

    public static String insertSql(String table) {
        return "INSERT INTO " + requireValidTable(table) + " (" + COLUMNS + ")"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    }

    public static String updateSql(String table) {
        return "UPDATE " + requireValidTable(table) + " SET config_value = ?, config_group = ?, description = ?,"
                + " encrypted = ?, enabled = ?, updated_at = ?, updated_by = ?, deleted = 0, version = ?, tenant_id = ?"
                + " WHERE config_key = ?";
    }

    public static String deleteSql(String table) {
        return "UPDATE " + requireValidTable(table) + " SET deleted = 1, updated_at = ? WHERE config_key = ?";
    }
}
