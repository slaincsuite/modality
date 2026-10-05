package cn.slainc.modules.infra.service;

import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.infra.InfraKeys;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

public class NoticeService {

    public record NoticeView(long id, String title, String content, String level, String status, Long publishTime,
                             Long expireTime, String operator, long createTime, long updateTime) {
    }

    private static final Set<String> LEVELS = Set.of("INFO", "WARN", "CRITICAL");

    private final JdbcTemplate jdbc;

    public NoticeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<NoticeView> page(long current, long size, String keyword, String status) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND title LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status.trim());
        }
        return JdbcPager.page(jdbc,
                "SELECT COUNT(*) FROM " + InfraKeys.NOTICE_TABLE + where,
                "SELECT id, title, content, level, status, publish_time, expire_time, operator,"
                        + " create_time, update_time FROM " + InfraKeys.NOTICE_TABLE + where + " ORDER BY id DESC",
                args, current, size, 500, NoticeService::mapRow);
    }

    public List<NoticeView> activeNotices() {
        long now = System.currentTimeMillis();
        return query(" WHERE status = 'PUBLISHED' AND publish_time <= ? AND (expire_time IS NULL OR expire_time > ?)"
                + " ORDER BY publish_time DESC LIMIT 100", List.of(now, now));
    }

    public NoticeView find(Long id) {
        List<NoticeView> rows = query(" WHERE id = ?", List.of(id));
        BizAssert.isTrue(!rows.isEmpty(), "公告不存在");
        return rows.get(0);
    }

    public NoticeView create(String title, String content, String level, Long expireTime, String operator) {
        BizAssert.hasText(title, "公告标题不能为空");
        BizAssert.hasText(content, "公告内容不能为空");
        String safeLevel = level == null || level.isBlank() ? "INFO" : level.toUpperCase();
        BizAssert.isTrue(LEVELS.contains(safeLevel), "公告级别只允许 INFO、WARN、CRITICAL");
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO " + InfraKeys.NOTICE_TABLE
                        + " (title, content, level, status, expire_time, operator, create_time, update_time)"
                        + " VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?)",
                title, content, safeLevel, expireTime, operator, now, now);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM " + InfraKeys.NOTICE_TABLE, Long.class);
        return find(id);
    }

    public NoticeView update(Long id, String title, String content, String level, Long expireTime) {
        NoticeView existing = find(id);
        BizAssert.isTrue("DRAFT".equals(existing.status()), "仅草稿状态的公告可以编辑，已发布公告请先下架");
        String safeLevel = level == null || level.isBlank() ? existing.level() : level.toUpperCase();
        BizAssert.isTrue(LEVELS.contains(safeLevel), "公告级别只允许 INFO、WARN、CRITICAL");
        jdbc.update("UPDATE " + InfraKeys.NOTICE_TABLE
                        + " SET title = COALESCE(?, title), content = COALESCE(?, content), level = ?,"
                        + " expire_time = COALESCE(?, expire_time), update_time = ? WHERE id = ?",
                title, content, safeLevel, expireTime, System.currentTimeMillis(), id);
        return find(id);
    }

    public NoticeView publish(Long id) {
        NoticeView existing = find(id);
        BizAssert.isTrue(!"PUBLISHED".equals(existing.status()), "公告已发布，请勿重复发布");
        jdbc.update("UPDATE " + InfraKeys.NOTICE_TABLE
                + " SET status = 'PUBLISHED', publish_time = ?, update_time = ? WHERE id = ?",
                System.currentTimeMillis(), System.currentTimeMillis(), id);
        return find(id);
    }

    public NoticeView offline(Long id) {
        find(id);
        jdbc.update("UPDATE " + InfraKeys.NOTICE_TABLE
                + " SET status = 'OFFLINE', update_time = ? WHERE id = ?", System.currentTimeMillis(), id);
        return find(id);
    }

    public void delete(Long id) {
        find(id);
        jdbc.update("DELETE FROM " + InfraKeys.NOTICE_TABLE + " WHERE id = ?", id);
    }

    private List<NoticeView> query(String suffix, List<Object> args) {
        return jdbc.query("SELECT id, title, content, level, status, publish_time, expire_time, operator,"
                        + " create_time, update_time FROM " + InfraKeys.NOTICE_TABLE + suffix,
                NoticeService::mapRow, args.toArray());
    }

    private static NoticeView mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NoticeView(rs.getLong("id"), rs.getString("title"), rs.getString("content"),
                rs.getString("level"), rs.getString("status"),
                rs.getObject("publish_time") == null ? null : rs.getLong("publish_time"),
                rs.getObject("expire_time") == null ? null : rs.getLong("expire_time"),
                rs.getString("operator"), rs.getLong("create_time"), rs.getLong("update_time"));
    }
}
