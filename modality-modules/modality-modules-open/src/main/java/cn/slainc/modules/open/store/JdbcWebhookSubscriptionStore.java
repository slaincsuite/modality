package cn.slainc.modules.open.store;

import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.entity.WebhookSubscription;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class JdbcWebhookSubscriptionStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWebhookSubscriptionStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insert(WebhookSubscription subscription) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        long now = System.currentTimeMillis();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE
                            + " (name, url, secret, events, enabled, max_retry, timeout_millis, version,"
                            + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, subscription.getName());
            statement.setString(2, subscription.getUrl());
            statement.setString(3, subscription.getSecret());
            statement.setString(4, subscription.getEvents());
            statement.setInt(5, Boolean.TRUE.equals(subscription.getEnabled()) ? 1 : 0);
            statement.setInt(6, subscription.getMaxRetry());
            statement.setLong(7, subscription.getTimeoutMillis());
            statement.setLong(8, now);
            statement.setLong(9, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) {
            subscription.setId(key.longValue());
        }
        return subscription.getId();
    }

    public WebhookSubscription findById(Long id) {
        if (id == null) {
            return null;
        }
        List<WebhookSubscription> found = jdbcTemplate.query(
                "SELECT * FROM " + OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE + " WHERE id = ?",
                JdbcWebhookSubscriptionStore::mapRow, id);
        return found.isEmpty() ? null : found.get(0);
    }

    public List<WebhookSubscription> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE
                + " WHERE id IN (" + placeholders + ")", JdbcWebhookSubscriptionStore::mapRow, ids.toArray());
    }

    public List<WebhookSubscription> findEnabled() {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE
                + " WHERE enabled = 1 ORDER BY id ASC", JdbcWebhookSubscriptionStore::mapRow);
    }

    public PageResult<WebhookSubscription> page(String name, Boolean enabled, long current, long size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            where.append(" AND name LIKE ?");
            args.add("%" + name.trim() + "%");
        }
        if (enabled != null) {
            where.append(" AND enabled = ?");
            args.add(enabled ? 1 : 0);
        }
        return JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM " + OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE + where,
                "SELECT * FROM " + OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE + where + " ORDER BY id DESC",
                args, current, size, JdbcWebhookSubscriptionStore::mapRow);
    }

    // 乐观锁更新：version 不匹配时返回 0 行，由上层提示刷新重试
    public int update(WebhookSubscription subscription) {
        return jdbcTemplate.update("UPDATE " + OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE
                + " SET name = ?, url = ?, secret = ?, events = ?, enabled = ?, max_retry = ?,"
                + " timeout_millis = ?, version = version + 1, updated_at = ?"
                + " WHERE id = ? AND version = ?",
                subscription.getName(), subscription.getUrl(), subscription.getSecret(), subscription.getEvents(),
                Boolean.TRUE.equals(subscription.getEnabled()) ? 1 : 0, subscription.getMaxRetry(),
                subscription.getTimeoutMillis(), System.currentTimeMillis(), subscription.getId(),
                subscription.getVersion());
    }

    public int deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.update("DELETE FROM " + OpenKeys.WEBHOOK_SUBSCRIPTION_TABLE
                + " WHERE id IN (" + placeholders + ")", ids.toArray());
    }

    private static WebhookSubscription mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new WebhookSubscription(rs.getLong("id"), rs.getString("name"), rs.getString("url"),
                rs.getString("secret"), rs.getString("events"), rs.getInt("enabled") == 1, rs.getInt("max_retry"),
                rs.getLong("timeout_millis"), rs.getInt("version"), rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }
}
