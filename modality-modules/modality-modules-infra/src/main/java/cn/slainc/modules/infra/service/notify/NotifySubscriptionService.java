package cn.slainc.modules.infra.service.notify;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.infra.InfraKeys;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class NotifySubscriptionService {

    public record SubscriptionView(long id, String serviceCode, String serviceName, List<String> channels,
                                   Long receiverId, String receiver, long messageCount30d, String latestTitle,
                                   boolean muted, long createTime, long updateTime) {
    }

    public record SubscriptionCommand(Long id, String serviceCode, String serviceName, List<String> channels,
                                      Long receiverId, Boolean muted) {
    }

    public record PreferenceView(boolean onlyUnverified, boolean hideMuted, boolean assistantFollowed) {
    }

    private record ReceiverName(long id, String name) {
    }

    private static final Set<String> CHANNELS = Set.of("INAPP", "EMAIL", "FEISHU", "PHONE");

    private static final long THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1000L;

    private final JdbcTemplate jdbc;

    public NotifySubscriptionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SubscriptionView> list(long userId) {
        long since = System.currentTimeMillis() - THIRTY_DAYS_MILLIS;
        return jdbc.query(
                "SELECT s.id, s.service_code, s.service_name, s.channels, s.receiver_id, s.receiver_name,"
                        + " s.muted, s.create_time, s.update_time,"
                        + " (SELECT COUNT(*) FROM " + InfraKeys.NOTIFY_RECORD_TABLE
                        + " r WHERE r.template_code = s.service_code AND r.create_time >= ?) AS message_count_30d,"
                        + " (SELECT r.title FROM " + InfraKeys.NOTIFY_RECORD_TABLE
                        + " r WHERE r.template_code = s.service_code ORDER BY r.id DESC LIMIT 1) AS latest_title"
                        + " FROM " + InfraKeys.NOTIFY_SUBSCRIPTION_TABLE
                        + " s WHERE s.tenant_id = ? AND s.user_id = ? ORDER BY s.service_name, s.id",
                (rs, rowNum) -> toView(rs), since, tenantId(), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<SubscriptionView> update(long userId, List<SubscriptionCommand> commands) {
        BizAssert.notEmpty(commands, "订阅配置不能为空");
        BizAssert.isTrue(commands.size() <= 100, "单次最多更新 100 条订阅");
        List<Long> affectedIds = new ArrayList<>();
        for (SubscriptionCommand command : commands) {
            BizAssert.notNull(command, "订阅配置不能为空");
            SubscriptionView existing = findExisting(userId, command);
            if (existing == null) {
                affectedIds.add(create(userId, command));
            } else {
                updateExisting(userId, existing, command);
                affectedIds.add(existing.id());
            }
        }
        return list(userId).stream().filter(view -> affectedIds.contains(view.id())).toList();
    }

    public PreferenceView preference(long userId) {
        List<PreferenceView> rows = jdbc.query(
                "SELECT only_unverified, hide_muted, assistant_followed FROM " + InfraKeys.NOTIFY_PREFERENCE_TABLE
                        + " WHERE tenant_id = ? AND user_id = ?",
                (rs, rowNum) -> new PreferenceView(rs.getInt("only_unverified") == 1,
                        rs.getInt("hide_muted") == 1, rs.getInt("assistant_followed") == 1),
                tenantId(), userId);
        return rows.isEmpty() ? new PreferenceView(false, false, false) : rows.get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public PreferenceView updatePreference(long userId, Boolean onlyUnverified, Boolean hideMuted) {
        BizAssert.isTrue(onlyUnverified != null || hideMuted != null, "至少需要修改一项列表设置");
        long now = System.currentTimeMillis();
        int updated = jdbc.update("UPDATE " + InfraKeys.NOTIFY_PREFERENCE_TABLE
                        + " SET only_unverified = COALESCE(?, only_unverified),"
                        + " hide_muted = COALESCE(?, hide_muted), update_time = ? WHERE tenant_id = ? AND user_id = ?",
                booleanValue(onlyUnverified), booleanValue(hideMuted), now, tenantId(), userId);
        if (updated == 0) {
            insertPreference(userId, Boolean.TRUE.equals(onlyUnverified), Boolean.TRUE.equals(hideMuted),
                    false, now);
        }
        return preference(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PreferenceView followAssistant(long userId) {
        long now = System.currentTimeMillis();
        int updated = jdbc.update("UPDATE " + InfraKeys.NOTIFY_PREFERENCE_TABLE
                        + " SET assistant_followed = 1, update_time = ? WHERE tenant_id = ? AND user_id = ?",
                now, tenantId(), userId);
        if (updated == 0) {
            insertPreference(userId, false, false, true, now);
        }
        return preference(userId);
    }

    private SubscriptionView findExisting(long userId, SubscriptionCommand command) {
        if (command.id() != null) {
            List<SubscriptionView> rows = jdbc.query(
                    "SELECT s.id, s.service_code, s.service_name, s.channels, s.receiver_id, s.receiver_name,"
                            + " s.muted, s.create_time, s.update_time, 0 AS message_count_30d,"
                            + " NULL AS latest_title FROM " + InfraKeys.NOTIFY_SUBSCRIPTION_TABLE
                            + " s WHERE s.id = ? AND s.tenant_id = ? AND s.user_id = ?",
                    (rs, rowNum) -> toView(rs), command.id(), tenantId(), userId);
            BizAssert.notEmpty(rows, "订阅不存在或不属于当前用户");
            return rows.get(0);
        }
        if (command.serviceCode() == null || command.serviceCode().isBlank()) {
            return null;
        }
        List<SubscriptionView> rows = jdbc.query(
                "SELECT s.id, s.service_code, s.service_name, s.channels, s.receiver_id, s.receiver_name,"
                        + " s.muted, s.create_time, s.update_time, 0 AS message_count_30d,"
                        + " NULL AS latest_title FROM " + InfraKeys.NOTIFY_SUBSCRIPTION_TABLE
                        + " s WHERE s.service_code = ? AND s.tenant_id = ? AND s.user_id = ?",
                (rs, rowNum) -> toView(rs), command.serviceCode().trim(), tenantId(), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long create(long userId, SubscriptionCommand command) {
        String serviceCode = trimToNull(command.serviceCode());
        String serviceName = trimToNull(command.serviceName());
        BizAssert.hasText(serviceCode, "服务编码不能为空");
        BizAssert.hasText(serviceName, "服务名称不能为空");
        BizAssert.isTrue(serviceCode.length() <= 64, "服务编码不能超过 64 个字符");
        BizAssert.isTrue(serviceName.length() <= 128, "服务名称不能超过 128 个字符");
        List<String> channels = normalizeChannels(command.channels());
        BizAssert.notEmpty(channels, "接收渠道不能为空");
        ReceiverName receiver = receiverName(command.receiverId());
        long now = System.currentTimeMillis();
        try {
            jdbc.update("INSERT INTO " + InfraKeys.NOTIFY_SUBSCRIPTION_TABLE
                            + " (tenant_id, user_id, service_code, service_name, channels, receiver_id,"
                            + " receiver_name, muted, create_time, update_time)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    tenantId(), userId, serviceCode, serviceName, String.join(",", channels),
                    receiver == null ? null : receiver.id(), receiver == null ? null : receiver.name(),
                    Boolean.TRUE.equals(command.muted()) ? 1 : 0, now, now);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "服务订阅已存在：" + serviceCode, exception);
        }
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM " + InfraKeys.NOTIFY_SUBSCRIPTION_TABLE
                + " WHERE tenant_id = ? AND user_id = ?", Long.class, tenantId(), userId);
        if (id == null) {
            throw new BizException(ResultCode.INTERNAL_SERVER_ERROR, "订阅创建失败：未返回记录编号");
        }
        return id;
    }

    private void updateExisting(long userId, SubscriptionView existing, SubscriptionCommand command) {
        String serviceName = command.serviceName() == null ? null : trimToNull(command.serviceName());
        List<String> channels = command.channels() == null ? null : normalizeChannels(command.channels());
        ReceiverName receiver = receiverName(command.receiverId());
        if (serviceName != null) {
            BizAssert.hasText(serviceName, "服务名称不能为空");
            BizAssert.isTrue(serviceName.length() <= 128, "服务名称不能超过 128 个字符");
        }
        if (channels != null) {
            BizAssert.notEmpty(channels, "接收渠道不能为空");
        }
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (serviceName != null) {
            sets.add("service_name = ?");
            args.add(serviceName);
        }
        if (channels != null) {
            sets.add("channels = ?");
            args.add(String.join(",", channels));
        }
        if (receiver != null) {
            sets.add("receiver_id = ?");
            sets.add("receiver_name = ?");
            args.add(receiver.id());
            args.add(receiver.name());
        }
        if (command.muted() != null) {
            sets.add("muted = ?");
            args.add(command.muted() ? 1 : 0);
        }
        BizAssert.notEmpty(sets, "至少需要修改一项订阅配置");
        sets.add("update_time = ?");
        args.add(System.currentTimeMillis());
        args.add(existing.id());
        args.add(tenantId());
        args.add(userId);
        jdbc.update("UPDATE " + InfraKeys.NOTIFY_SUBSCRIPTION_TABLE + " SET " + String.join(", ", sets)
                + " WHERE id = ? AND tenant_id = ? AND user_id = ?", args.toArray());
    }

    private ReceiverName receiverName(Long receiverId) {
        if (receiverId == null) {
            return null;
        }
        List<ReceiverName> rows = jdbc.query(
                "SELECT id, name FROM " + InfraKeys.NOTIFY_RECEIVER_TABLE + " WHERE id = ? AND tenant_id = ?",
                (rs, rowNum) -> new ReceiverName(rs.getLong("id"), rs.getString("name")),
                receiverId, tenantId());
        BizAssert.notEmpty(rows, "接收人不存在或不属于当前租户");
        return rows.get(0);
    }

    private void insertPreference(long userId, boolean onlyUnverified, boolean hideMuted, boolean assistantFollowed,
                                  long now) {
        try {
            jdbc.update("INSERT INTO " + InfraKeys.NOTIFY_PREFERENCE_TABLE
                            + " (tenant_id, user_id, only_unverified, hide_muted, assistant_followed, create_time,"
                            + " update_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    tenantId(), userId, onlyUnverified ? 1 : 0, hideMuted ? 1 : 0,
                    assistantFollowed ? 1 : 0, now, now);
        } catch (DuplicateKeyException exception) {
            jdbc.update("UPDATE " + InfraKeys.NOTIFY_PREFERENCE_TABLE
                            + " SET only_unverified = COALESCE(?, only_unverified),"
                            + " hide_muted = COALESCE(?, hide_muted), assistant_followed = COALESCE(?, assistant_followed),"
                            + " update_time = ? WHERE tenant_id = ? AND user_id = ?",
                    onlyUnverified ? 1 : null, hideMuted ? 1 : null, assistantFollowed ? 1 : null,
                    now, tenantId(), userId);
        }
    }

    private static SubscriptionView toView(java.sql.ResultSet rs) throws java.sql.SQLException {
        long receiverId = rs.getLong("receiver_id");
        Long nullableReceiverId = rs.wasNull() ? null : receiverId;
        String persistedReceiver = rs.getString("receiver_name");
        Object rawReceiverId = rs.getObject("receiver_id");
        return new SubscriptionView(rs.getLong("id"), rs.getString("service_code"), rs.getString("service_name"),
                splitChannels(rs.getString("channels")), nullableReceiverId,
                persistedReceiver == null && rawReceiverId != null ? String.valueOf(rawReceiverId) : persistedReceiver,
                rs.getLong("message_count_30d"), rs.getString("latest_title"), rs.getInt("muted") == 1,
                rs.getLong("create_time"), rs.getLong("update_time"));
    }

    private static List<String> splitChannels(String channels) {
        if (channels == null || channels.isBlank()) {
            return List.of();
        }
        return List.of(channels.split(","));
    }

    private static List<String> normalizeChannels(List<String> channels) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (channels != null) {
            for (String raw : channels) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                for (String part : raw.split("[,，]")) {
                    String value = part.trim().toUpperCase(Locale.ROOT);
                    value = switch (value) {
                        case "站内信" -> "INAPP";
                        case "邮件" -> "EMAIL";
                        case "飞书" -> "FEISHU";
                        case "短信" -> "PHONE";
                        default -> value;
                    };
                    BizAssert.isTrue(CHANNELS.contains(value), "接收渠道只允许 INAPP、EMAIL、FEISHU、PHONE");
                    result.add(value);
                }
            }
        }
        return List.copyOf(result);
    }

    private static Integer booleanValue(Boolean value) {
        return value == null ? null : value ? 1 : 0;
    }

    private static long tenantId() {
        return TenantContext.getOrDefault();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
