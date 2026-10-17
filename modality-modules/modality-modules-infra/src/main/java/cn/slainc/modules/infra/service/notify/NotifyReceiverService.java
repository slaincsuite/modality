package cn.slainc.modules.infra.service.notify;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.jdbc.JdbcPager;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.utils.string.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

public class NotifyReceiverService {

    public record ReceiverView(long id, String name, String type, String accountId, String phone, String email,
                               String feishuId, boolean phoneVerified, boolean emailVerified, boolean feishuVerified,
                               boolean nightReceive, boolean smsMute, boolean marketingMute, Long lastVerifyTime,
                               long createTime, long updateTime) {
    }

    public record ReceiverCreateCommand(String name, String type, String accountId, String phone, String email,
                                        String feishuId) {
    }

    public record ContactCommand(String phone, String email, String feishuId) {
    }

    public record PreferenceCommand(Boolean nightReceive, Boolean smsMute, Boolean marketingMute) {
    }

    public record VerifyResult(long receiverId, boolean verified, List<String> channels, String verificationToken,
                               Long expireTime) {
    }

    private record ReceiverRow(long id, String name, String type, String accountId, String phone, String email,
                               String feishuId, boolean phoneVerified, boolean emailVerified, boolean feishuVerified,
                               boolean nightReceive, boolean smsMute, boolean marketingMute, String verifyTokenHash,
                               String verifyChannels, Long verifyExpireTime, Long lastVerifyTime, long createTime,
                               long updateTime) {
    }

    private static final Set<String> RECEIVER_TYPES = Set.of("USER", "COLLABORATOR");

    private static final Set<String> CHANNELS = Set.of("PHONE", "EMAIL", "FEISHU");

    private static final Pattern PHONE = Pattern.compile("^[0-9+\\- ]{6,32}$");

    private static final long VERIFY_TTL_MILLIS = 15L * 60L * 1000L;

    private final JdbcTemplate jdbc;

    public NotifyReceiverService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<ReceiverView> page(long current, long size, String keyword, String type) {
        StringBuilder where = new StringBuilder(" WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId());
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (name LIKE ? OR account_id LIKE ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (type != null && !type.isBlank()) {
            where.append(" AND receiver_type = ?");
            args.add(normalizeType(type));
        }
        return JdbcPager.page(jdbc,
                "SELECT COUNT(*) FROM " + InfraKeys.NOTIFY_RECEIVER_TABLE + where,
                "SELECT id, name, receiver_type, account_id, phone, email, feishu_id, phone_verified,"
                        + " email_verified, feishu_verified, night_receive, sms_mute, marketing_mute,"
                        + " verify_token_hash, verify_channels, verify_expire_time, last_verify_time,"
                        + " create_time, update_time FROM " + InfraKeys.NOTIFY_RECEIVER_TABLE + where
                        + " ORDER BY id DESC",
                args, current, size, 200, (rs, rowNum) -> toView(mapRow(rs)));
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiverView create(ReceiverCreateCommand command) {
        BizAssert.notNull(command, "接收人信息不能为空");
        String name = trim(command.name());
        BizAssert.hasText(name, "接收人名称不能为空");
        BizAssert.isTrue(name.length() <= 128, "接收人名称不能超过 128 个字符");
        String type = normalizeType(command.type());
        String accountId = trimToNull(command.accountId());
        String phone = trimToNull(command.phone());
        String email = trimToNull(command.email());
        String feishuId = trimToNull(command.feishuId());
        validateAccountId(accountId);
        validateContacts(phone, email, feishuId);
        long now = System.currentTimeMillis();
        try {
            long id = insert(commandInsert(name, type, accountId, phone, email, feishuId, tenantId(), now));
            return find(id);
        } catch (DuplicateKeyException exception) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "账号 ID 已绑定其他接收人：" + accountId, exception);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> receiverIds) {
        List<Long> ids = receiverIds == null ? List.of() : receiverIds.stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        BizAssert.notEmpty(ids, "接收人编号不能为空");
        BizAssert.isTrue(ids.size() <= 200, "单次最多删除 200 个接收人");
        long tenantId = tenantId();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(tenantId);
        countArgs.addAll(ids);
        Long matched = jdbc.queryForObject("SELECT COUNT(*) FROM " + InfraKeys.NOTIFY_RECEIVER_TABLE
                        + " WHERE tenant_id = ? AND id IN (" + placeholders + ")", Long.class, countArgs.toArray());
        BizAssert.isTrue(matched != null && matched == ids.size(), "部分接收人不存在或不属于当前租户");
        List<Object> subscriptionArgs = new ArrayList<>();
        subscriptionArgs.add(tenantId);
        subscriptionArgs.addAll(ids);
        jdbc.update("UPDATE " + InfraKeys.NOTIFY_SUBSCRIPTION_TABLE + " SET receiver_id = NULL, update_time = ?"
                        + " WHERE tenant_id = ? AND receiver_id IN (" + placeholders + ")",
                subscriptionArgsWithTime(subscriptionArgs, System.currentTimeMillis()).toArray());
        List<Object> deleteArgs = new ArrayList<>();
        deleteArgs.add(tenantId);
        deleteArgs.addAll(ids);
        return jdbc.update("DELETE FROM " + InfraKeys.NOTIFY_RECEIVER_TABLE
                + " WHERE tenant_id = ? AND id IN (" + placeholders + ")", deleteArgs.toArray());
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiverView updateContacts(long id, ContactCommand command) {
        BizAssert.notNull(command, "联系方式不能为空");
        ReceiverRow row = requireRow(id);
        String phone = command.phone() == null ? null : trimToNull(command.phone());
        String email = command.email() == null ? null : trimToNull(command.email());
        String feishuId = command.feishuId() == null ? null : trimToNull(command.feishuId());
        validateContacts(phone, email, feishuId);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (command.phone() != null) {
            sets.add("phone = ?");
            sets.add("phone_verified = 0");
            args.add(phone);
        }
        if (command.email() != null) {
            sets.add("email = ?");
            sets.add("email_verified = 0");
            args.add(email);
        }
        if (command.feishuId() != null) {
            sets.add("feishu_id = ?");
            sets.add("feishu_verified = 0");
            args.add(feishuId);
        }
        BizAssert.notEmpty(sets, "至少需要修改一项联系方式");
        sets.add("update_time = ?");
        args.add(System.currentTimeMillis());
        args.add(id);
        args.add(tenantId());
        jdbc.update("UPDATE " + InfraKeys.NOTIFY_RECEIVER_TABLE + " SET " + String.join(", ", sets)
                + " WHERE id = ? AND tenant_id = ?", args.toArray());
        return find(row.id());
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiverView updatePreferences(long id, PreferenceCommand command) {
        BizAssert.notNull(command, "接收偏好不能为空");
        requireRow(id);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        appendBoolean(sets, args, "night_receive", command.nightReceive());
        appendBoolean(sets, args, "sms_mute", command.smsMute());
        appendBoolean(sets, args, "marketing_mute", command.marketingMute());
        BizAssert.notEmpty(sets, "至少需要修改一项接收偏好");
        sets.add("update_time = ?");
        args.add(System.currentTimeMillis());
        args.add(id);
        args.add(tenantId());
        jdbc.update("UPDATE " + InfraKeys.NOTIFY_RECEIVER_TABLE + " SET " + String.join(", ", sets)
                + " WHERE id = ? AND tenant_id = ?", args.toArray());
        return find(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public VerifyResult verify(long id, List<String> requestedChannels, String token) {
        ReceiverRow row = requireRow(id);
        if (token != null && !token.isBlank()) {
            return confirmVerification(row, requestedChannels, token.trim());
        }
        List<String> channels = pendingChannels(row, requestedChannels);
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        long expireTime = System.currentTimeMillis() + VERIFY_TTL_MILLIS;
        jdbc.update("UPDATE " + InfraKeys.NOTIFY_RECEIVER_TABLE
                        + " SET verify_token_hash = ?, verify_channels = ?, verify_expire_time = ?,"
                        + " verify_sent_time = ?, update_time = ? WHERE id = ? AND tenant_id = ?",
                hash(rawToken), String.join(",", channels), expireTime, System.currentTimeMillis(),
                System.currentTimeMillis(), id, tenantId());
        return new VerifyResult(id, false, channels, rawToken, expireTime);
    }

    public ReceiverView find(long id) {
        return toView(requireRow(id));
    }

    private VerifyResult confirmVerification(ReceiverRow row, List<String> requestedChannels, String token) {
        BizAssert.isTrue(row.verifyTokenHash() != null, "接收人未发起验证");
        BizAssert.isTrue(row.verifyExpireTime() != null && row.verifyExpireTime() >= System.currentTimeMillis(),
                "验证链接已过期，请重新发送");
        BizAssert.isTrue(MessageDigest.isEqual(row.verifyTokenHash().getBytes(StandardCharsets.UTF_8),
                hash(token).getBytes(StandardCharsets.UTF_8)), "验证令牌不正确");
        List<String> issuedChannels = splitChannels(row.verifyChannels());
        List<String> channels = requestedChannels == null || requestedChannels.isEmpty()
                ? issuedChannels : normalizeChannels(requestedChannels);
        BizAssert.isTrue(!channels.isEmpty() && issuedChannels.containsAll(channels), "验证渠道与发送记录不一致");
        List<String> sets = new ArrayList<>();
        for (String channel : channels) {
            sets.add(switch (channel) {
                case "PHONE" -> "phone_verified = 1";
                case "EMAIL" -> "email_verified = 1";
                default -> "feishu_verified = 1";
            });
        }
        long now = System.currentTimeMillis();
        sets.add("verify_token_hash = NULL");
        sets.add("verify_channels = NULL");
        sets.add("verify_expire_time = NULL");
        sets.add("last_verify_time = ?");
        sets.add("update_time = ?");
        List<Object> args = new ArrayList<>();
        args.add(now);
        args.add(now);
        args.add(row.id());
        args.add(tenantId());
        jdbc.update("UPDATE " + InfraKeys.NOTIFY_RECEIVER_TABLE + " SET " + String.join(", ", sets)
                + " WHERE id = ? AND tenant_id = ?", args.toArray());
        return new VerifyResult(row.id(), true, channels, null, null);
    }

    private List<String> pendingChannels(ReceiverRow row, List<String> requestedChannels) {
        List<String> requested = requestedChannels == null || requestedChannels.isEmpty()
                ? configuredChannels(row) : normalizeChannels(requestedChannels);
        BizAssert.isTrue(requested.stream().allMatch(channel -> configured(row, channel)),
                "消息渠道未配置对应联系方式");
        List<String> pending = requested.stream().filter(channel -> !verified(row, channel)).toList();
        BizAssert.notEmpty(pending, "接收人没有待验证的消息渠道");
        return pending;
    }

    private static boolean configured(ReceiverRow row, String channel) {
        return switch (channel) {
            case "PHONE" -> StringUtils.isNotBlank(row.phone());
            case "EMAIL" -> StringUtils.isNotBlank(row.email());
            default -> StringUtils.isNotBlank(row.feishuId());
        };
    }

    private static List<String> configuredChannels(ReceiverRow row) {
        List<String> channels = new ArrayList<>();
        if (StringUtils.isNotBlank(row.phone())) {
            channels.add("PHONE");
        }
        if (StringUtils.isNotBlank(row.email())) {
            channels.add("EMAIL");
        }
        if (StringUtils.isNotBlank(row.feishuId())) {
            channels.add("FEISHU");
        }
        return channels;
    }

    private static boolean verified(ReceiverRow row, String channel) {
        return switch (channel) {
            case "PHONE" -> row.phoneVerified();
            case "EMAIL" -> row.emailVerified();
            default -> row.feishuVerified();
        };
    }

    private ReceiverRow requireRow(long id) {
        List<ReceiverRow> rows = jdbc.query(
                "SELECT id, name, receiver_type, account_id, phone, email, feishu_id, phone_verified,"
                        + " email_verified, feishu_verified, night_receive, sms_mute, marketing_mute,"
                        + " verify_token_hash, verify_channels, verify_expire_time, last_verify_time,"
                        + " create_time, update_time FROM " + InfraKeys.NOTIFY_RECEIVER_TABLE
                        + " WHERE id = ? AND tenant_id = ?",
                (rs, rowNum) -> mapRow(rs), id, tenantId());
        BizAssert.notEmpty(rows, "接收人不存在或不属于当前租户");
        return rows.get(0);
    }

    private static ReceiverRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReceiverRow(rs.getLong("id"), rs.getString("name"), rs.getString("receiver_type"),
                rs.getString("account_id"), rs.getString("phone"), rs.getString("email"), rs.getString("feishu_id"),
                rs.getInt("phone_verified") == 1, rs.getInt("email_verified") == 1,
                rs.getInt("feishu_verified") == 1, rs.getInt("night_receive") == 1,
                rs.getInt("sms_mute") == 1, rs.getInt("marketing_mute") == 1,
                rs.getString("verify_token_hash"), rs.getString("verify_channels"),
                rs.getObject("verify_expire_time") == null ? null : rs.getLong("verify_expire_time"),
                rs.getObject("last_verify_time") == null ? null : rs.getLong("last_verify_time"),
                rs.getLong("create_time"), rs.getLong("update_time"));
    }

    private static ReceiverView toView(ReceiverRow row) {
        return new ReceiverView(row.id(), row.name(), row.type(), row.accountId(),
                StringUtils.maskMobile(row.phone()), StringUtils.maskEmail(row.email()),
                StringUtils.maskMobile(row.feishuId()), row.phoneVerified(), row.emailVerified(),
                row.feishuVerified(), row.nightReceive(), row.smsMute(), row.marketingMute(),
                row.lastVerifyTime(), row.createTime(), row.updateTime());
    }

    private long insert(InsertReceiver insert) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(insert.sql(), Statement.RETURN_GENERATED_KEYS);
            int index = 1;
            for (Object value : insert.args()) {
                statement.setObject(index++, value);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(ResultCode.INTERNAL_SERVER_ERROR, "接收人创建失败：未返回记录编号");
        }
        return key.longValue();
    }

    private static InsertReceiver commandInsert(String name, String type, String accountId, String phone,
                                                String email, String feishuId, long tenantId, long now) {
        String sql = "INSERT INTO " + InfraKeys.NOTIFY_RECEIVER_TABLE
                + " (tenant_id, name, receiver_type, account_id, phone, email, feishu_id, phone_verified,"
                + " email_verified, feishu_verified, night_receive, sms_mute, marketing_mute,"
                + " create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 1, 0, 0, ?, ?)";
        return new InsertReceiver(sql,
                Arrays.asList(tenantId, name, type, accountId, phone, email, feishuId, now, now));
    }

    private record InsertReceiver(String sql, List<Object> args) {
    }

    private static List<Object> subscriptionArgsWithTime(List<Object> args, long now) {
        List<Object> values = new ArrayList<>(args.size() + 1);
        values.add(now);
        values.addAll(args);
        return values;
    }

    private static void appendBoolean(List<String> sets, List<Object> args, String column, Boolean value) {
        if (value != null) {
            sets.add(column + " = ?");
            args.add(value ? 1 : 0);
        }
    }

    private static void validateAccountId(String accountId) {
        if (accountId != null) {
            BizAssert.isTrue(accountId.length() <= 64, "账号 ID 不能超过 64 个字符");
        }
    }

    private static void validateContacts(String phone, String email, String feishuId) {
        if (phone != null && !phone.isBlank()) {
            BizAssert.isTrue(phone.length() <= 32 && PHONE.matcher(phone).matches(), "手机号格式不正确");
        }
        if (email != null && !email.isBlank()) {
            BizAssert.isTrue(email.length() <= 128 && email.contains("@"), "邮箱格式不正确");
        }
        if (feishuId != null && !feishuId.isBlank()) {
            BizAssert.isTrue(feishuId.length() <= 128, "飞书账号不能超过 128 个字符");
        }
    }

    private static String normalizeType(String type) {
        String value = type == null || type.isBlank() ? "USER" : type.trim().toUpperCase(Locale.ROOT);
        value = switch (value) {
            case "用户" -> "USER";
            case "协作者" -> "COLLABORATOR";
            default -> value;
        };
        BizAssert.isTrue(RECEIVER_TYPES.contains(value), "接收人类型只允许 USER、COLLABORATOR");
        return value;
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
                        case "短信" -> "PHONE";
                        case "邮件" -> "EMAIL";
                        case "飞书" -> "FEISHU";
                        default -> value;
                    };
                    BizAssert.isTrue(CHANNELS.contains(value), "消息渠道只允许 PHONE、EMAIL、FEISHU");
                    result.add(value);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<String> splitChannels(String channels) {
        if (channels == null || channels.isBlank()) {
            return List.of();
        }
        return List.of(channels.split(","));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new BizException(ResultCode.INTERNAL_SERVER_ERROR, "验证令牌生成失败", exception);
        }
    }

    private static long tenantId() {
        return TenantContext.getOrDefault();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
