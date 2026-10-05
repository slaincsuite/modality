package cn.slainc.modules.infra.service;

import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.infra.InfraKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;

public class DictService {

    public record DictTypeView(long id, String code, String name, String remark, boolean enabled, long createTime,
                               long updateTime, long itemCount) {
    }

    public record DictItemView(long id, String typeCode, String label, String value, int sort, boolean enabled,
                               String remark, long createTime, long updateTime) {
    }

    private final JdbcTemplate jdbc;

    private final Map<String, List<DictItemView>> cache = new ConcurrentHashMap<>();

    public DictService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<DictTypeView> typePage(long current, long size, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (code LIKE ? OR name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        return JdbcPager.page(jdbc,
                "SELECT COUNT(*) FROM " + InfraKeys.DICT_TYPE_TABLE + where,
                "SELECT t.id, t.code, t.name, t.remark, t.enabled, t.create_time, t.update_time,"
                        + " (SELECT COUNT(*) FROM " + InfraKeys.DICT_ITEM_TABLE + " i WHERE i.type_code = t.code)"
                        + " AS item_count FROM " + InfraKeys.DICT_TYPE_TABLE + " t" + where
                        + " ORDER BY t.code",
                args, current, size, 500,
                (rs, rowNum) -> new DictTypeView(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("remark"), rs.getInt("enabled") == 1, rs.getLong("create_time"),
                        rs.getLong("update_time"), rs.getLong("item_count")));
    }

    public void createType(String code, String name, String remark) {
        BizAssert.hasText(code, "字典编码不能为空");
        BizAssert.hasText(name, "字典名称不能为空");
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + InfraKeys.DICT_TYPE_TABLE + " WHERE code = ?", Integer.class, code);
        BizAssert.isTrue(existing == null || existing == 0, "字典编码已存在：" + code);
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO " + InfraKeys.DICT_TYPE_TABLE
                + " (code, name, remark, enabled, create_time, update_time) VALUES (?, ?, ?, 1, ?, ?)",
                code, name, remark, now, now);
    }

    public void updateType(String code, String name, String remark, Boolean enabled) {
        BizAssert.isTrue(findType(code) != null, "字典类型不存在：" + code);
        long now = System.currentTimeMillis();
        jdbc.update("UPDATE " + InfraKeys.DICT_TYPE_TABLE
                        + " SET name = COALESCE(?, name), remark = COALESCE(?, remark),"
                        + " enabled = COALESCE(?, enabled), update_time = ? WHERE code = ?",
                name, remark, enabled == null ? null : (enabled ? 1 : 0), now, code);
    }

    public void deleteType(String code) {
        BizAssert.isTrue(findType(code) != null, "字典类型不存在：" + code);
        Integer items = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + InfraKeys.DICT_ITEM_TABLE + " WHERE type_code = ?", Integer.class, code);
        BizAssert.isTrue(items == null || items == 0, "字典类型下仍有字典项，先清空字典项再删除");
        jdbc.update("DELETE FROM " + InfraKeys.DICT_TYPE_TABLE + " WHERE code = ?", code);
    }

    public List<DictItemView> itemsByType(String typeCode) {
        return cache.computeIfAbsent(typeCode, code -> jdbc.query(
                "SELECT id, type_code, label, item_value, sort, enabled, remark, create_time, update_time FROM "
                        + InfraKeys.DICT_ITEM_TABLE + " WHERE type_code = ? AND enabled = 1 ORDER BY sort, id",
                (rs, rowNum) -> new DictItemView(rs.getLong("id"), rs.getString("type_code"), rs.getString("label"),
                        rs.getString("item_value"), rs.getInt("sort"), rs.getInt("enabled") == 1, rs.getString("remark"),
                        rs.getLong("create_time"), rs.getLong("update_time")),
                code));
    }

    public PageResult<DictItemView> itemPage(long current, long size, String typeCode, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (typeCode != null && !typeCode.isBlank()) {
            where.append(" AND type_code = ?");
            args.add(typeCode.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (label LIKE ? OR item_value LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        return JdbcPager.page(jdbc,
                "SELECT COUNT(*) FROM " + InfraKeys.DICT_ITEM_TABLE + where,
                "SELECT id, type_code, label, item_value, sort, enabled, remark, create_time, update_time FROM "
                        + InfraKeys.DICT_ITEM_TABLE + where + " ORDER BY type_code, sort, id",
                args, current, size, 500,
                (rs, rowNum) -> new DictItemView(rs.getLong("id"), rs.getString("type_code"), rs.getString("label"),
                        rs.getString("item_value"), rs.getInt("sort"), rs.getInt("enabled") == 1,
                        rs.getString("remark"), rs.getLong("create_time"), rs.getLong("update_time")));
    }

    public void createItem(String typeCode, String label, String value, Integer sort, String remark) {
        BizAssert.hasText(typeCode, "字典编码不能为空");
        BizAssert.hasText(label, "字典标签不能为空");
        BizAssert.hasText(value, "字典值不能为空");
        BizAssert.isTrue(findType(typeCode) != null, "字典类型不存在：" + typeCode);
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + InfraKeys.DICT_ITEM_TABLE + " WHERE type_code = ? AND item_value = ?",
                Integer.class, typeCode, value);
        BizAssert.isTrue(existing == null || existing == 0, "字典值已存在：" + typeCode + " / " + value);
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO " + InfraKeys.DICT_ITEM_TABLE
                        + " (type_code, label, item_value, sort, enabled, remark, create_time, update_time)"
                        + " VALUES (?, ?, ?, ?, 1, ?, ?, ?)",
                typeCode, label, value, sort == null ? 0 : sort, remark, now, now);
        cache.remove(typeCode);
    }

    public void updateItem(long id, String label, String value, Integer sort, Boolean enabled, String remark) {
        DictItemView item = findItem(id);
        BizAssert.isTrue(item != null, "字典项不存在");
        long now = System.currentTimeMillis();
        jdbc.update("UPDATE " + InfraKeys.DICT_ITEM_TABLE
                        + " SET label = COALESCE(?, label), item_value = COALESCE(?, item_value),"
                        + " sort = COALESCE(?, sort), enabled = COALESCE(?, enabled),"
                        + " remark = COALESCE(?, remark), update_time = ? WHERE id = ?",
                label, value, sort, enabled == null ? null : (enabled ? 1 : 0), remark, now, id);
        cache.remove(item.typeCode());
    }

    public void deleteItem(long id) {
        DictItemView item = findItem(id);
        BizAssert.isTrue(item != null, "字典项不存在");
        jdbc.update("DELETE FROM " + InfraKeys.DICT_ITEM_TABLE + " WHERE id = ?", id);
        cache.remove(item.typeCode());
    }

    public String resolveLabel(String typeCode, String value) {
        return itemsByType(typeCode).stream()
                .filter(item -> item.value().equals(value))
                .map(DictItemView::label)
                .findFirst()
                .orElse(null);
    }

    public DictItemView findItemByValue(String typeCode, String value) {
        List<DictItemView> rows = jdbc.query(
                "SELECT id, type_code, label, item_value, sort, enabled, remark, create_time, update_time FROM "
                        + InfraKeys.DICT_ITEM_TABLE + " WHERE type_code = ? AND item_value = ?",
                (rs, rowNum) -> new DictItemView(rs.getLong("id"), rs.getString("type_code"), rs.getString("label"),
                        rs.getString("item_value"), rs.getInt("sort"), rs.getInt("enabled") == 1, rs.getString("remark"),
                        rs.getLong("create_time"), rs.getLong("update_time")),
                typeCode, value);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long cacheSize() {
        return cache.size();
    }

    public void clearCache() {
        cache.clear();
    }

    private DictTypeView findType(String code) {
        List<DictTypeView> rows = jdbc.query(
                "SELECT t.id, t.code, t.name, t.remark, t.enabled, t.create_time, t.update_time, 0 AS item_count"
                        + " FROM " + InfraKeys.DICT_TYPE_TABLE + " t WHERE t.code = ?",
                (rs, rowNum) -> new DictTypeView(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("remark"), rs.getInt("enabled") == 1, rs.getLong("create_time"),
                        rs.getLong("update_time"), 0),
                code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DictItemView findItem(long id) {
        List<DictItemView> rows = jdbc.query(
                "SELECT id, type_code, label, item_value, sort, enabled, remark, create_time, update_time FROM "
                        + InfraKeys.DICT_ITEM_TABLE + " WHERE id = ?",
                (rs, rowNum) -> new DictItemView(rs.getLong("id"), rs.getString("type_code"), rs.getString("label"),
                        rs.getString("item_value"), rs.getInt("sort"), rs.getInt("enabled") == 1, rs.getString("remark"),
                        rs.getLong("create_time"), rs.getLong("update_time")),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
