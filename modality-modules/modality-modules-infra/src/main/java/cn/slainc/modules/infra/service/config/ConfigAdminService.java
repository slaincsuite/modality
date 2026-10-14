package cn.slainc.modules.infra.service.config;

import cn.slainc.modules.infra.InfraKeys;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.slainc.modules.infra.store.ConfigHistoryStore;

public class ConfigAdminService {

    public record ConfigView(String key, String group, String description, boolean encrypted, boolean enabled,
                             int version, String valuePreview) {
    }

    public record RawRow(String configValue, String configGroup, String description, boolean encrypted) {
    }

    public record HistoryView(long id, String configKey, String configGroup, String operation, String operator,
                              long createTime) {
    }

    public record ExportItem(String key, String value, String group, String description, boolean encrypted) {
    }

    public record ImportSummary(int succeeded, int failed, List<String> errors) {
    }

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:\\-]{1,255}");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;

    private final ObjectProvider<DynamicConfigService> configService;

    private final ConfigHistoryStore history;

    public ConfigAdminService(JdbcTemplate jdbc, ObjectProvider<DynamicConfigService> configService,
                              ConfigHistoryStore history) {
        this.jdbc = jdbc;
        this.configService = configService;
        this.history = history;
    }

    public PageResult<ConfigView> page(long current, long size, String keyword, String group) {
        StringBuilder where = new StringBuilder(" WHERE deleted = 0");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (config_key LIKE ? OR description LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (group != null && !group.isBlank()) {
            where.append(" AND config_group = ?");
            args.add(group.trim());
        }
        return JdbcPager.page(jdbc,
                "SELECT COUNT(*) FROM t_dynamic_config" + where,
                "SELECT config_key, config_group, description, encrypted, enabled, version, config_value"
                        + " FROM t_dynamic_config" + where + " ORDER BY config_key",
                args, current, size, 500,
                (rs, rowNum) -> new ConfigView(rs.getString("config_key"), rs.getString("config_group"),
                        rs.getString("description"), rs.getInt("encrypted") == 1, rs.getInt("enabled") == 1,
                        rs.getInt("version"),
                        rs.getInt("encrypted") == 1 ? InfraKeys.MASKED_VALUE : preview(rs.getString("config_value"))));
    }

    public ConfigView detail(String key) {
        RawRow raw = findRaw(key);
        BizAssert.found(raw != null, "动态配置不存在：" + key);
        Integer version = jdbc.queryForObject(
                "SELECT version FROM t_dynamic_config WHERE config_key = ? AND deleted = 0", Integer.class, key);
        Integer enabled = jdbc.queryForObject(
                "SELECT enabled FROM t_dynamic_config WHERE config_key = ? AND deleted = 0", Integer.class, key);
        return new ConfigView(key, raw.configGroup(), raw.description(), raw.encrypted(),
                enabled != null && enabled == 1, version == null ? 0 : version,
                raw.encrypted() ? InfraKeys.MASKED_VALUE : preview(raw.configValue()));
    }

    public List<String> groups() {
        return jdbc.queryForList(
                "SELECT DISTINCT config_group FROM t_dynamic_config WHERE deleted = 0 AND config_group IS NOT NULL"
                        + " ORDER BY config_group", String.class);
    }

    public void create(String key, String value, String group, String description, boolean encrypted) {
        BizAssert.hasText(key, "配置键不能为空");
        BizAssert.isTrue(KEY_PATTERN.matcher(key).matches(), "配置键只允许字母、数字、点、冒号、下划线与中划线");
        BizAssert.notNull(value, "配置值不能为空");
        BizAssert.isTrue(findRaw(key) == null, "配置键已存在：" + key);
        writeValue(key, value, group, encrypted);
        updateDescription(key, description);
        history.record(key, group, description, null, value, encrypted, "CREATE", operator());
    }

    public void update(String key, String value, String group, String description) {
        RawRow raw = findRaw(key);
        BizAssert.found(raw != null, "动态配置不存在：" + key);
        String oldPlain = raw.encrypted() ? decrypted(key) : raw.configValue();
        boolean groupChanged = group != null && !group.isBlank() && !group.equals(raw.configGroup());
        boolean descriptionChanged = description != null && !description.equals(raw.description());
        if (value != null) {
            writeValue(key, value, group == null || group.isBlank() ? raw.configGroup() : group, raw.encrypted());
        } else if (groupChanged) {
            writeValue(key, oldPlain, group, raw.encrypted());
        }
        if (descriptionChanged) {
            updateDescription(key, description);
        }
        history.record(key, group == null || group.isBlank() ? raw.configGroup() : group, description, oldPlain,
                value == null ? oldPlain : value, raw.encrypted(), "UPDATE", operator());
    }

    public void delete(String key) {
        RawRow raw = findRaw(key);
        BizAssert.found(raw != null, "动态配置不存在：" + key);
        String oldPlain = raw.encrypted() ? decrypted(key) : raw.configValue();
        DynamicConfigService service = configService.getIfAvailable();
        if (service != null) {
            service.remove(key);
        } else {
            jdbc.update("UPDATE t_dynamic_config SET deleted = 1, updated_at = ? WHERE config_key = ?",
                    LocalDateTime.now(), key);
        }
        history.record(key, raw.configGroup(), raw.description(), oldPlain, null, raw.encrypted(), "DELETE",
                operator());
    }

    public PageResult<HistoryView> historyPage(String key, long current, long size) {
        BizAssert.hasText(key, "配置键不能为空");
        return history.page(key, current, size).map(row -> new HistoryView(row.id(), row.configKey(),
                row.configGroup(), row.operation(), row.operator(), row.createTime()));
    }

    public void rollback(Long historyId) {
        ConfigHistoryStore.HistoryRow row = history.find(historyId);
        BizAssert.found(row != null, "配置历史记录不存在");
        if (row.oldValue() == null) {
            RawRow raw = findRaw(row.configKey());
            BizAssert.isTrue(raw != null, "回滚失败：该配置已被删除且历史中无创建前值，无法撤销创建");
            delete(row.configKey());
            return;
        }
        if (row.encrypted()) {
            BizAssert.isTrue(configService.getIfAvailable() != null, "当前环境未启用动态配置服务，无法回滚加密配置");
        }
        writeValue(row.configKey(), row.oldValue(), row.configGroup(), row.encrypted());
        updateDescription(row.configKey(), row.description());
        history.record(row.configKey(), row.configGroup(), row.description(), null, row.oldValue(), row.encrypted(),
                "ROLLBACK", operator());
    }

    public List<ExportItem> exportItems(String group, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE deleted = 0");
        List<Object> args = new ArrayList<>();
        if (group != null && !group.isBlank()) {
            where.append(" AND config_group = ?");
            args.add(group.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (config_key LIKE ? OR description LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        return jdbc.query("SELECT config_key, config_value, config_group, description, encrypted FROM t_dynamic_config"
                        + where + " ORDER BY config_key",
                (rs, rowNum) -> new ExportItem(rs.getString("config_key"),
                        rs.getInt("encrypted") == 1 ? null : rs.getString("config_value"),
                        rs.getString("config_group"), rs.getString("description"), rs.getInt("encrypted") == 1),
                args.toArray());
    }

    public ImportSummary importItems(List<Map<String, Object>> items) {
        BizAssert.notEmpty(items, "导入清单不能为空");
        int succeeded = 0;
        List<String> errors = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String key = String.valueOf(item.get("key"));
            try {
                BizAssert.hasText(key, "配置键不能为空");
                BizAssert.isTrue(!"null".equals(key), "配置键不能为空");
                String value = item.get("value") == null ? null : String.valueOf(item.get("value"));
                String group = item.get("group") == null ? null : String.valueOf(item.get("group"));
                String description = item.get("description") == null ? null : String.valueOf(item.get("description"));
                boolean encrypted = Boolean.parseBoolean(String.valueOf(item.getOrDefault("encrypted", "false")));
                if (findRaw(key) == null) {
                    BizAssert.notNull(value, "新增配置必须提供值");
                    create(key, value, group, description, encrypted);
                } else {
                    update(key, value, group, description);
                }
                succeeded++;
            } catch (Exception exception) {
                errors.add(key + "：" + exception.getMessage());
            }
        }
        return new ImportSummary(succeeded, errors.size(), errors);
    }

    private void writeValue(String key, String value, String group, boolean encrypted) {
        DynamicConfigService service = configService.getIfAvailable();
        if (service != null) {
            if (encrypted) {
                service.setEncrypted(key, value, group);
            } else {
                service.set(key, value, group);
            }
            return;
        }
        BizAssert.isTrue(!encrypted, "当前环境未启用动态配置服务，无法写入加密配置");
        LocalDateTime now = LocalDateTime.now();
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_dynamic_config WHERE config_key = ? AND deleted = 0", Integer.class, key);
        if (active != null && active > 0) {
            jdbc.update("UPDATE t_dynamic_config SET config_value = ?, config_group = ?, updated_at = ?,"
                    + " version = version + 1 WHERE config_key = ? AND deleted = 0", value, group, now, key);
            return;
        }
        // 软删过的行仍占用 uk_config_key 唯一键：复活原行而不是插入新行
        Integer deletedRow = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_dynamic_config WHERE config_key = ? AND deleted = 1", Integer.class, key);
        if (deletedRow != null && deletedRow > 0) {
            jdbc.update("UPDATE t_dynamic_config SET config_value = ?, config_group = ?, encrypted = 0,"
                    + " updated_at = ?, version = version + 1, deleted = 0 WHERE config_key = ?",
                    value, group, now, key);
            return;
        }
        long id = System.currentTimeMillis() * 1000 + RANDOM.nextInt(1000);
        jdbc.update("INSERT INTO t_dynamic_config (id, config_key, config_value, config_group, description,"
                + " encrypted, enabled, created_at, updated_at, deleted, version, tenant_id)"
                + " VALUES (?, ?, ?, ?, NULL, 0, 1, ?, ?, 0, 0, 1)", id, key, value, group, now, now);
    }

    private void updateDescription(String key, String description) {
        if (description == null) {
            return;
        }
        jdbc.update("UPDATE t_dynamic_config SET description = ?, updated_at = ? WHERE config_key = ? AND deleted = 0",
                description, LocalDateTime.now(), key);
    }

    private String decrypted(String key) {
        DynamicConfigService service = configService.getIfAvailable();
        if (service == null) {
            throw new BizException(ResultCode.BUSINESS_ERROR, "当前环境未启用动态配置服务，无法读取加密配置明文");
        }
        return service.getEncrypted(key);
    }

    public RawRow findRaw(String key) {
        List<RawRow> rows = jdbc.query(
                "SELECT config_value, config_group, description, encrypted FROM t_dynamic_config"
                        + " WHERE config_key = ? AND deleted = 0",
                (rs, rowNum) -> new RawRow(rs.getString("config_value"), rs.getString("config_group"),
                        rs.getString("description"), rs.getInt("encrypted") == 1),
                key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String preview(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "…";
    }

    public String operator() {
        cn.slainc.core.context.UserContext.UserInfo user = cn.slainc.core.context.UserContext.get();
        if (user == null) {
            return "系统";
        }
        if (user.username() != null && !user.username().isBlank()) {
            return user.username();
        }
        return user.userId() == null ? "系统" : String.valueOf(user.userId());
    }
}
