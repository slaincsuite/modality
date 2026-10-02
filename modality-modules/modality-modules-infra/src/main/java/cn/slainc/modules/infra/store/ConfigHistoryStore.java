package cn.slainc.modules.infra.store;

import cn.slainc.modules.infra.InfraKeys;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public class ConfigHistoryStore {

    public record HistoryRow(Long id, String configKey, String configGroup, String description, String oldValue,
                             String newValue, boolean encrypted, String operation, String operator, long createTime) {
    }

    private final JdbcTemplate jdbc;

    private final ConfigHistoryCipher cipher;

    public ConfigHistoryStore(JdbcTemplate jdbc, ConfigHistoryCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    public void record(String configKey, String configGroup, String description, String oldPlain, String newPlain,
                       boolean encrypted, String operation, String operator) {
        jdbc.update("INSERT INTO " + InfraKeys.CONFIG_HISTORY_TABLE
                        + " (config_key, config_group, description, old_value, new_value, encrypted, operation,"
                        + " operator, create_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                configKey, configGroup, description, cipher.encrypt(oldPlain), cipher.encrypt(newPlain),
                encrypted ? 1 : 0, operation, operator, System.currentTimeMillis());
    }

    public List<HistoryRow> page(String configKey, long offset, int size) {
        return jdbc.query("SELECT id, config_key, config_group, description, old_value, new_value, encrypted,"
                        + " operation, operator, create_time FROM " + InfraKeys.CONFIG_HISTORY_TABLE
                        + " WHERE config_key = ? ORDER BY id DESC LIMIT " + size + " OFFSET " + offset,
                (rs, rowNum) -> new HistoryRow(rs.getLong("id"), rs.getString("config_key"),
                        rs.getString("config_group"), rs.getString("description"),
                        cipher.decrypt(rs.getString("old_value")), cipher.decrypt(rs.getString("new_value")),
                        rs.getInt("encrypted") == 1, rs.getString("operation"), rs.getString("operator"),
                        rs.getLong("create_time")),
                configKey);
    }

    public long count(String configKey) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + InfraKeys.CONFIG_HISTORY_TABLE + " WHERE config_key = ?", Long.class,
                configKey);
        return count == null ? 0 : count;
    }

    public HistoryRow find(Long id) {
        List<HistoryRow> rows = jdbc.query("SELECT id, config_key, config_group, description, old_value, new_value,"
                        + " encrypted, operation, operator, create_time FROM " + InfraKeys.CONFIG_HISTORY_TABLE
                        + " WHERE id = ?",
                (rs, rowNum) -> new HistoryRow(rs.getLong("id"), rs.getString("config_key"),
                        rs.getString("config_group"), rs.getString("description"),
                        cipher.decrypt(rs.getString("old_value")), cipher.decrypt(rs.getString("new_value")),
                        rs.getInt("encrypted") == 1, rs.getString("operation"), rs.getString("operator"),
                        rs.getLong("create_time")),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
