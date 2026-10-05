package cn.slainc.modules.infra.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.web.websocket.WebSocketPusher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

public class NotifyService {

    public record TemplateView(long id, String code, String name, String titleTemplate, String contentTemplate,
                               String channel, boolean enabled) {
    }

    public record MessageView(long id, long receiverId, String loginType, String title, String content, String level,
                              String templateCode, boolean read, Long readTime, long createTime) {
    }

    public record WebhookView(long id, String name, String platform, String url, boolean enabled) {
    }

    public record RecordView(long id, String channel, String target, String templateCode, String title,
                             String content, String status, String error, int retryCount, long createTime,
                             Long sentTime) {
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final JdbcTemplate jdbc;

    private final NotifyWebhookClient webhookClient;

    private final WebSocketPusher pusher;

    private final int maxRetry;

    public NotifyService(JdbcTemplate jdbc, NotifyWebhookClient webhookClient,
                         WebSocketPusher pusher, int maxRetry) {
        this.jdbc = jdbc;
        this.webhookClient = webhookClient;
        this.pusher = pusher;
        this.maxRetry = maxRetry;
    }

    public PageResult<TemplateView> templatePage(long current, long size, String keyword) {
        String where = keyword == null || keyword.isBlank() ? "" : " WHERE code LIKE ? OR name LIKE ?";
        List<Object> args = where.isEmpty() ? List.of()
                : List.of("%" + keyword.trim() + "%", "%" + keyword.trim() + "%");
        return JdbcPager.page(jdbc,
                "SELECT COUNT(*) FROM " + InfraKeys.NOTIFY_TEMPLATE_TABLE + where,
                "SELECT id, code, name, title_template, content_template, channel, enabled FROM "
                        + InfraKeys.NOTIFY_TEMPLATE_TABLE + where + " ORDER BY code",
                args, current, size, 500,
                (rs, rowNum) -> new TemplateView(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("title_template"), rs.getString("content_template"), rs.getString("channel"),
                        rs.getInt("enabled") == 1));
    }

    public void createTemplate(String code, String name, String titleTemplate, String contentTemplate, String channel) {
        BizAssert.hasText(code, "模板编码不能为空");
        BizAssert.hasText(name, "模板名称不能为空");
        BizAssert.hasText(titleTemplate, "标题模板不能为空");
        BizAssert.hasText(contentTemplate, "内容模板不能为空");
        String safeChannel = channel == null || channel.isBlank() ? "INAPP" : channel.toUpperCase();
        BizAssert.isTrue(List.of("INAPP", "WEBHOOK").contains(safeChannel), "模板渠道只允许 INAPP、WEBHOOK");
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + InfraKeys.NOTIFY_TEMPLATE_TABLE + " WHERE code = ?", Integer.class, code);
        BizAssert.isTrue(existing == null || existing == 0, "模板编码已存在：" + code);
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO " + InfraKeys.NOTIFY_TEMPLATE_TABLE
                        + " (code, name, title_template, content_template, channel, enabled, create_time, update_time)"
                        + " VALUES (?, ?, ?, ?, ?, 1, ?, ?)",
                code, name, titleTemplate, contentTemplate, safeChannel, now, now);
    }

    public void updateTemplate(long id, String name, String titleTemplate, String contentTemplate, Boolean enabled) {
        BizAssert.isTrue(findTemplate(id) != null, "通知模板不存在");
        jdbc.update("UPDATE " + InfraKeys.NOTIFY_TEMPLATE_TABLE
                        + " SET name = COALESCE(?, name), title_template = COALESCE(?, title_template),"
                        + " content_template = COALESCE(?, content_template),"
                        + " enabled = COALESCE(?, enabled), update_time = ? WHERE id = ?",
                name, titleTemplate, contentTemplate, enabled == null ? null : (enabled ? 1 : 0),
                System.currentTimeMillis(), id);
    }

    public void deleteTemplate(long id) {
        BizAssert.isTrue(findTemplate(id) != null, "通知模板不存在");
        jdbc.update("DELETE FROM " + InfraKeys.NOTIFY_TEMPLATE_TABLE + " WHERE id = ?", id);
    }

    public long sendInApp(String templateCode, Map<String, String> params, List<Long> receivers, String loginType) {
        TemplateView template = findTemplateByCode(templateCode);
        BizAssert.isTrue(template != null, "通知模板不存在：" + templateCode);
        BizAssert.isTrue(template.enabled(), "通知模板已停用：" + templateCode);
        BizAssert.notEmpty(receivers, "站内信收件人不能为空");
        String safeLoginType = loginType == null || loginType.isBlank() ? "TENANT_USER" : loginType.toUpperCase();
        String title = render(template.titleTemplate(), params);
        String content = render(template.contentTemplate(), params);
        long now = System.currentTimeMillis();
        for (Long receiver : receivers) {
            BizAssert.notNull(receiver, "收件人编号不能为空");
            jdbc.update("INSERT INTO " + InfraKeys.NOTIFY_MESSAGE_TABLE
                            + " (receiver_id, login_type, title, content, level, template_code, read_flag,"
                            + " create_time) VALUES (?, ?, ?, ?, 'INFO', ?, 0, ?)",
                    receiver, safeLoginType, title, content, templateCode, now);
            Map<String, Object> push = new LinkedHashMap<>();
            push.put("topic", InfraKeys.DEFAULT_NOTIFY_TOPIC);
            push.put("title", title);
            pusher.sendToUser(String.valueOf(receiver), push);
        }
        String target = receivers.stream().map(receiver -> "user:" + receiver).reduce((a, b) -> a + "," + b).orElse("");
        jdbc.update("INSERT INTO " + InfraKeys.NOTIFY_RECORD_TABLE
                        + " (channel, target, template_code, title, content, status, create_time, sent_time)"
                        + " VALUES ('INAPP', ?, ?, ?, ?, 'SUCCESS', ?, ?)",
                target, templateCode, title, content, now, now);
        return receivers.size();
    }

    public RecordView sendWebhook(long webhookId, String templateCode, Map<String, String> params) {
        WebhookView webhook = findWebhook(webhookId);
        BizAssert.isTrue(webhook != null, "Webhook 配置不存在");
        BizAssert.isTrue(webhook.enabled(), "Webhook 已停用");
        String title;
        String content;
        if (templateCode != null && !templateCode.isBlank()) {
            TemplateView template = findTemplateByCode(templateCode);
            BizAssert.isTrue(template != null, "通知模板不存在：" + templateCode);
            title = render(template.titleTemplate(), params);
            content = render(template.contentTemplate(), params);
        } else {
            title = params == null ? "系统通知" : params.getOrDefault("title", "系统通知");
            content = params == null ? "" : params.getOrDefault("content", "");
        }
        NotifyWebhookClient.WebhookConfig config = webhookConfig(webhook.id());
        NotifyWebhookClient.SendOutcome outcome = webhookClient.send(config, title, content, "INFO");
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO " + InfraKeys.NOTIFY_RECORD_TABLE
                        + " (channel, target, template_code, title, content, status, error, create_time, sent_time)"
                        + " VALUES ('WEBHOOK', ?, ?, ?, ?, ?, ?, ?, ?)",
                "webhook:" + webhookId, templateCode, title, content, outcome.status(), outcome.error(), now,
                "SUCCESS".equals(outcome.status()) ? now : null);
        if ("FAILED".equals(outcome.status())) {
            throw new BizException(ResultCode.THIRD_PARTY_ERROR, "Webhook 发送失败：" + outcome.error());
        }
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM " + InfraKeys.NOTIFY_RECORD_TABLE, Long.class);
        return findRecord(id);
    }

    public RecordView retryRecord(long id) {
        RecordView record = findRecord(id);
        BizAssert.isTrue(record != null, "发送记录不存在");
        BizAssert.isTrue("FAILED".equals(record.status()), "只有失败的发送记录可以重试");
        BizAssert.isTrue(record.retryCount() < maxRetry, "重试次数已达上限（" + maxRetry + " 次）");
        String[] targetParts = record.target() == null ? new String[0] : record.target().split(":", 2);
        BizAssert.isTrue("WEBHOOK".equals(record.channel()) && targetParts.length == 2,
                "仅 Webhook 渠道的发送记录支持重试");
        WebhookView webhook = findWebhook(Long.parseLong(targetParts[1]));
        BizAssert.isTrue(webhook != null, "Webhook 配置已不存在：" + record.target());
        NotifyWebhookClient.SendOutcome outcome = webhookClient.send(webhookConfig(webhook.id()), record.title(),
                record.content() == null ? "" : record.content(), "INFO");
        jdbc.update("UPDATE " + InfraKeys.NOTIFY_RECORD_TABLE
                        + " SET status = ?, error = ?, retry_count = retry_count + 1, sent_time = ? WHERE id = ?",
                outcome.status(), outcome.error(),
                "SUCCESS".equals(outcome.status()) ? System.currentTimeMillis() : null, id);
        RecordView retried = findRecord(id);
        if ("FAILED".equals(outcome.status())) {
            throw new BizException(ResultCode.THIRD_PARTY_ERROR, "重试仍失败：" + outcome.error());
        }
        return retried;
    }

    public PageResult<WebhookView> webhookPage(long current, long size) {
        return JdbcPager.page(jdbc,
                "SELECT COUNT(*) FROM " + InfraKeys.NOTIFY_WEBHOOK_TABLE,
                "SELECT id, name, platform, url, enabled FROM " + InfraKeys.NOTIFY_WEBHOOK_TABLE + " ORDER BY id",
                List.of(), current, size, 500,
                (rs, rowNum) -> new WebhookView(rs.getLong("id"), rs.getString("name"), rs.getString("platform"),
                        rs.getString("url"), rs.getInt("enabled") == 1));
    }

    public void createWebhook(String name, String platform, String url, String secret) {
        BizAssert.hasText(name, "Webhook 名称不能为空");
        BizAssert.hasText(url, "Webhook 地址不能为空");
        String safePlatform = platform == null || platform.isBlank() ? "CUSTOM" : platform.toUpperCase();
        long now = System.currentTimeMillis();
        jdbc.update("INSERT INTO " + InfraKeys.NOTIFY_WEBHOOK_TABLE
                        + " (name, platform, url, secret, enabled, create_time, update_time)"
                        + " VALUES (?, ?, ?, ?, 1, ?, ?)", name, safePlatform, url, secret, now, now);
    }

    public void updateWebhook(long id, String name, String platform, String url, String secret, Boolean enabled) {
        BizAssert.isTrue(findWebhook(id) != null, "Webhook 配置不存在");
        jdbc.update("UPDATE " + InfraKeys.NOTIFY_WEBHOOK_TABLE
                        + " SET name = COALESCE(?, name), platform = COALESCE(?, platform), url = COALESCE(?, url),"
                        + " secret = COALESCE(?, secret), enabled = COALESCE(?, enabled), update_time = ? WHERE id = ?",
                name, platform == null ? null : platform.toUpperCase(), url, secret,
                enabled == null ? null : (enabled ? 1 : 0), System.currentTimeMillis(), id);
    }

    public void deleteWebhook(long id) {
        BizAssert.isTrue(findWebhook(id) != null, "Webhook 配置不存在");
        jdbc.update("DELETE FROM " + InfraKeys.NOTIFY_WEBHOOK_TABLE + " WHERE id = ?", id);
    }

    public PageResult<RecordView> recordPage(long current, long size, String channel, String status) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (channel != null && !channel.isBlank()) {
            where.append(" AND channel = ?");
            args.add(channel.trim().toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status.trim().toUpperCase());
        }
        return JdbcPager.page(jdbc,
                "SELECT COUNT(*) FROM " + InfraKeys.NOTIFY_RECORD_TABLE + where,
                "SELECT id, channel, target, template_code, title, content, status, error, retry_count, create_time, sent_time"
                        + " FROM " + InfraKeys.NOTIFY_RECORD_TABLE + where + " ORDER BY id DESC",
                args, current, size, 500,
                (rs, rowNum) -> new RecordView(rs.getLong("id"), rs.getString("channel"), rs.getString("target"),
                        rs.getString("template_code"), rs.getString("title"), rs.getString("content"),
                        rs.getString("status"), rs.getString("error"), rs.getInt("retry_count"),
                        rs.getLong("create_time"), rs.getObject("sent_time") == null ? null : rs.getLong("sent_time")));
    }

    public PageResult<MessageView> myMessages(long receiverId, String loginType, long current, long size,
                                              Boolean read) {
        StringBuilder where = new StringBuilder(" WHERE receiver_id = ? AND login_type = ?");
        List<Object> args = new ArrayList<>(List.of(receiverId, loginType));
        if (read != null) {
            where.append(" AND read_flag = ?");
            args.add(read ? 1 : 0);
        }
        return JdbcPager.page(jdbc,
                "SELECT COUNT(*) FROM " + InfraKeys.NOTIFY_MESSAGE_TABLE + where,
                "SELECT id, receiver_id, login_type, title, content, level, template_code, read_flag, read_time,"
                        + " create_time FROM " + InfraKeys.NOTIFY_MESSAGE_TABLE + where + " ORDER BY id DESC",
                args, current, size, 500,
                (rs, rowNum) -> new MessageView(rs.getLong("id"), rs.getLong("receiver_id"),
                        rs.getString("login_type"), rs.getString("title"), rs.getString("content"),
                        rs.getString("level"), rs.getString("template_code"), rs.getInt("read_flag") == 1,
                        rs.getObject("read_time") == null ? null : rs.getLong("read_time"),
                        rs.getLong("create_time")));
    }

    public long unreadCount(long receiverId, String loginType) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + InfraKeys.NOTIFY_MESSAGE_TABLE
                        + " WHERE receiver_id = ? AND login_type = ? AND read_flag = 0", Long.class,
                receiverId, loginType);
        return count == null ? 0 : count;
    }

    public void markRead(long receiverId, String loginType, long messageId) {
        int updated = jdbc.update("UPDATE " + InfraKeys.NOTIFY_MESSAGE_TABLE
                + " SET read_flag = 1, read_time = ? WHERE id = ? AND receiver_id = ? AND login_type = ?",
                System.currentTimeMillis(), messageId, receiverId, loginType);
        BizAssert.isTrue(updated > 0, "站内信不存在或不属于当前用户");
    }

    public long markAllRead(long receiverId, String loginType) {
        return jdbc.update("UPDATE " + InfraKeys.NOTIFY_MESSAGE_TABLE
                + " SET read_flag = 1, read_time = ? WHERE receiver_id = ? AND login_type = ? AND read_flag = 0",
                System.currentTimeMillis(), receiverId, loginType);
    }

    public void deleteMessage(long receiverId, String loginType, long messageId) {
        int updated = jdbc.update("DELETE FROM " + InfraKeys.NOTIFY_MESSAGE_TABLE
                + " WHERE id = ? AND receiver_id = ? AND login_type = ?", messageId, receiverId, loginType);
        BizAssert.isTrue(updated > 0, "站内信不存在或不属于当前用户");
    }

    public static String render(String template, Map<String, String> params) {
        if (template == null) {
            return "";
        }
        if (params == null || params.isEmpty()) {
            return PLACEHOLDER.matcher(template).replaceAll("");
        }
        return PLACEHOLDER.matcher(template).replaceAll(match -> {
            String value = params.get(match.group(1));
            return value == null ? "" : value.replace("\\", "\\\\").replace("$", "\\$");
        });
    }

    private NotifyWebhookClient.WebhookConfig webhookConfig(long webhookId) {
        List<String> secrets = jdbc.query(
                "SELECT secret FROM " + InfraKeys.NOTIFY_WEBHOOK_TABLE + " WHERE id = ?",
                (rs, rowNum) -> rs.getString("secret"), webhookId);
        WebhookView webhook = findWebhook(webhookId);
        return new NotifyWebhookClient.WebhookConfig(webhook.id(), webhook.name(), webhook.platform(),
                webhook.url(), secrets.isEmpty() ? null : secrets.get(0));
    }

    private TemplateView findTemplate(long id) {
        List<TemplateView> rows = jdbc.query(
                "SELECT id, code, name, title_template, content_template, channel, enabled FROM "
                        + InfraKeys.NOTIFY_TEMPLATE_TABLE + " WHERE id = ?",
                (rs, rowNum) -> new TemplateView(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("title_template"), rs.getString("content_template"), rs.getString("channel"),
                        rs.getInt("enabled") == 1),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private TemplateView findTemplateByCode(String code) {
        List<TemplateView> rows = jdbc.query(
                "SELECT id, code, name, title_template, content_template, channel, enabled FROM "
                        + InfraKeys.NOTIFY_TEMPLATE_TABLE + " WHERE code = ?",
                (rs, rowNum) -> new TemplateView(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("title_template"), rs.getString("content_template"), rs.getString("channel"),
                        rs.getInt("enabled") == 1),
                code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private WebhookView findWebhook(long id) {
        List<WebhookView> rows = jdbc.query(
                "SELECT id, name, platform, url, enabled FROM " + InfraKeys.NOTIFY_WEBHOOK_TABLE + " WHERE id = ?",
                (rs, rowNum) -> new WebhookView(rs.getLong("id"), rs.getString("name"), rs.getString("platform"),
                        rs.getString("url"), rs.getInt("enabled") == 1),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public RecordView findRecord(long id) {
        List<RecordView> rows = jdbc.query(
                "SELECT id, channel, target, template_code, title, content, status, error, retry_count, create_time, sent_time"
                        + " FROM " + InfraKeys.NOTIFY_RECORD_TABLE + " WHERE id = ?",
                (rs, rowNum) -> new RecordView(rs.getLong("id"), rs.getString("channel"), rs.getString("target"),
                        rs.getString("template_code"), rs.getString("title"), rs.getString("content"), rs.getString("status"),
                        rs.getString("error"), rs.getInt("retry_count"), rs.getLong("create_time"),
                        rs.getObject("sent_time") == null ? null : rs.getLong("sent_time")),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
