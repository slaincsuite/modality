package cn.slainc.modules.open.store;

import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.entity.WebhookDelivery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JdbcWebhookDeliveryStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWebhookDeliveryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long insertPending(WebhookDelivery delivery) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        long now = System.currentTimeMillis();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + OpenKeys.WEBHOOK_DELIVERY_TABLE
                            + " (subscription_id, event_type, payload, status, attempt, next_retry_at,"
                            + " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, delivery.getSubscriptionId());
            statement.setString(2, delivery.getEventType());
            statement.setString(3, delivery.getPayload());
            statement.setString(4, delivery.getStatus());
            statement.setInt(5, delivery.getAttempt() == null ? 0 : delivery.getAttempt());
            if (delivery.getNextRetryAt() == null) {
                statement.setLong(6, now);
            } else {
                statement.setLong(6, delivery.getNextRetryAt());
            }
            statement.setLong(7, now);
            statement.setLong(8, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public WebhookDelivery findById(Long id) {
        if (id == null) {
            return null;
        }
        List<WebhookDelivery> found = jdbcTemplate.query(
                "SELECT * FROM " + OpenKeys.WEBHOOK_DELIVERY_TABLE + " WHERE id = ?",
                JdbcWebhookDeliveryStore::mapRow, id);
        return found.isEmpty() ? null : found.get(0);
    }

    // 到期扫描：status IN (PENDING, FAILED) 且 next_retry_at 已到，按 id 升序取一批
    public List<WebhookDelivery> findDue(int limit) {
        return jdbcTemplate.query("SELECT * FROM " + OpenKeys.WEBHOOK_DELIVERY_TABLE
                + " WHERE status IN ('PENDING', 'FAILED') AND next_retry_at <= ? ORDER BY id ASC LIMIT "
                + Math.max(limit, 1), JdbcWebhookDeliveryStore::mapRow, System.currentTimeMillis());
    }

    public PageResult<WebhookDelivery> page(Long subscriptionId, String eventType, String status, long current,
                                            long size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (subscriptionId != null) {
            where.append(" AND subscription_id = ?");
            args.add(subscriptionId);
        }
        if (eventType != null && !eventType.isBlank()) {
            where.append(" AND event_type = ?");
            args.add(eventType.trim());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status.trim());
        }
        return JdbcPager.page(jdbcTemplate,
                "SELECT COUNT(*) FROM " + OpenKeys.WEBHOOK_DELIVERY_TABLE + where,
                "SELECT * FROM " + OpenKeys.WEBHOOK_DELIVERY_TABLE + where + " ORDER BY id DESC",
                args, current, size, JdbcWebhookDeliveryStore::mapRow);
    }

    public void markSuccess(Long id, int httpStatus, String responseBody, long costMillis) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update("UPDATE " + OpenKeys.WEBHOOK_DELIVERY_TABLE
                + " SET status = 'SUCCESS', http_status = ?, response_body = ?, error_message = NULL,"
                + " next_retry_at = NULL, cost_millis = ?, updated_at = ? WHERE id = ?",
                httpStatus > 0 ? httpStatus : null, responseBody, costMillis, System.currentTimeMillis(), id);
    }

    public void markFailure(Long id, int attempt, String status, int httpStatus, String responseBody,
                            String errorMessage, LocalDateTime nextRetryAt, long costMillis) {
        if (id == null) {
            return;
        }
        Long nextRetryAtMillis = nextRetryAt == null ? null
                : Timestamp.valueOf(nextRetryAt).getTime();
        jdbcTemplate.update("UPDATE " + OpenKeys.WEBHOOK_DELIVERY_TABLE
                        + " SET status = ?, attempt = ?, http_status = ?, response_body = ?, error_message = ?,"
                        + " next_retry_at = ?, cost_millis = ?, updated_at = ? WHERE id = ?",
                status, attempt, httpStatus > 0 ? httpStatus : null, responseBody, errorMessage, nextRetryAtMillis,
                costMillis, System.currentTimeMillis(), id);
    }

    public void resetForRetry(Long id) {
        long now = System.currentTimeMillis();
        jdbcTemplate.update("UPDATE " + OpenKeys.WEBHOOK_DELIVERY_TABLE
                + " SET status = 'PENDING', next_retry_at = ?, updated_at = ? WHERE id = ?", now, now, id);
    }

    public int deleteBySubscriptionIds(List<Long> subscriptionIds) {
        if (subscriptionIds == null || subscriptionIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(subscriptionIds.size(), "?"));
        return jdbcTemplate.update("DELETE FROM " + OpenKeys.WEBHOOK_DELIVERY_TABLE
                + " WHERE subscription_id IN (" + placeholders + ")", subscriptionIds.toArray());
    }

    private static WebhookDelivery mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new WebhookDelivery(rs.getLong("id"), rs.getLong("subscription_id"), rs.getString("event_type"),
                rs.getString("payload"), rs.getString("status"), rs.getInt("attempt"),
                (Integer) rs.getObject("http_status"), rs.getString("response_body"),
                rs.getString("error_message"), (Long) rs.getObject("next_retry_at"),
                (Long) rs.getObject("cost_millis"), rs.getLong("created_at"),
                (Long) rs.getObject("updated_at"));
    }
}
