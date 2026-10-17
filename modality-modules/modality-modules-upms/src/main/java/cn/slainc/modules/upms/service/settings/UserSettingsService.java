package cn.slainc.modules.upms.service.settings;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.dto.UserSettingsRequest;
import cn.slainc.modules.upms.entity.UserSettingsHistoryRecord;
import cn.slainc.modules.upms.entity.UserSettingsRecord;
import cn.slainc.modules.upms.mapper.RoleMapper;
import cn.slainc.modules.upms.mapper.UserSettingsHistoryMapper;
import cn.slainc.modules.upms.mapper.UserSettingsMapper;
import cn.slainc.utils.json.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class UserSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private static final int HISTORY_LIMIT = 200;

    private final UserSettingsMapper settingsMapper;

    private final UserSettingsHistoryMapper historyMapper;

    private final RoleMapper roleMapper;

    private final ObjectProvider<DynamicConfigService> configService;

    public UserSettingsService(UserSettingsMapper settingsMapper, UserSettingsHistoryMapper historyMapper,
                               RoleMapper roleMapper, ObjectProvider<DynamicConfigService> configService) {
        this.settingsMapper = settingsMapper;
        this.historyMapper = historyMapper;
        this.roleMapper = roleMapper;
        this.configService = configService;
    }

    public UserSettingsRecord current() {
        UserSettingsRecord record = settingsMapper.selectById(UserSettingsRecord.SINGLETON_ID);
        return record == null ? defaults() : record;
    }

    @Transactional
    public UserSettingsRecord update(UserSettingsRequest request, String operator) {
        if (request == null) {
            throw new BizException("用户设置内容不能为空");
        }
        UserSettingsRecord before = current();
        UserSettingsRecord target = copy(before);
        merge(target, request);
        validate(target);
        long now = System.currentTimeMillis();
        target.setId(UserSettingsRecord.SINGLETON_ID);
        if (target.getCreateTime() == null) {
            target.setCreateTime(now);
        }
        target.setUpdateTime(now);
        if (settingsMapper.selectById(UserSettingsRecord.SINGLETON_ID) == null) {
            settingsMapper.insert(target);
        } else {
            settingsMapper.updateById(target);
        }
        recordHistory(before, target, operator, now);
        applyDynamicConfig(target);
        return target;
    }

    public List<UserSettingsHistoryRecord> history(String settingKey, Integer limit) {
        int safeLimit = Math.min(Math.max(limit == null ? HISTORY_LIMIT : limit, 1), HISTORY_LIMIT);
        return historyMapper.selectList(new LambdaQueryWrapper<UserSettingsHistoryRecord>()
                .eq(settingKey != null && !settingKey.isBlank(), UserSettingsHistoryRecord::getSettingKey, settingKey)
                .orderByDesc(UserSettingsHistoryRecord::getId)
                .last("LIMIT " + safeLimit));
    }

    private void merge(UserSettingsRecord target, UserSettingsRequest request) {
        if (request.passwordMinLength() != null) {
            target.setPasswordMinLength(request.passwordMinLength());
        }
        if (request.passwordRequireClasses() != null) {
            target.setPasswordRequireClasses(request.passwordRequireClasses());
        }
        if (request.passwordExpireDays() != null) {
            target.setPasswordExpireDays(request.passwordExpireDays());
        }
        if (request.passwordAllowSelfReset() != null) {
            target.setPasswordAllowSelfReset(request.passwordAllowSelfReset());
        }
        if (request.defaultLoginType() != null) {
            target.setDefaultLoginType(request.defaultLoginType());
        }
        if (request.defaultRoleId() != null) {
            target.setDefaultRoleId(request.defaultRoleId());
        }
        if (request.sendInitialPasswordEmail() != null) {
            target.setSendInitialPasswordEmail(request.sendInitialPasswordEmail());
        }
        if (request.forceChangePassword() != null) {
            target.setForceChangePassword(request.forceChangePassword());
        }
        if (request.loginFailureThreshold() != null) {
            target.setLoginFailureThreshold(request.loginFailureThreshold());
        }
        if (request.loginLockMinutes() != null) {
            target.setLoginLockMinutes(request.loginLockMinutes());
        }
        if (request.idleTimeoutMinutes() != null) {
            target.setIdleTimeoutMinutes(request.idleTimeoutMinutes());
        }
        if (request.maxSessionMinutes() != null) {
            target.setMaxSessionMinutes(request.maxSessionMinutes());
        }
        if (request.restrictIdleUsers() != null) {
            target.setRestrictIdleUsers(request.restrictIdleUsers());
        }
        if (request.idleUserDays() != null) {
            target.setIdleUserDays(request.idleUserDays());
        }
    }

    private void validate(UserSettingsRecord settings) {
        requireRange(settings.getPasswordMinLength(), 8, 32, "密码最短长度");
        requireRange(settings.getPasswordRequireClasses(), 1, 4, "密码字符类型数量");
        requireRange(settings.getPasswordExpireDays(), 0, 365, "密码有效期");
        requireRange(settings.getLoginFailureThreshold(), 1, 20, "登录失败次数阈值");
        requireRange(settings.getLoginLockMinutes(), 1, 1440, "登录锁定分钟数");
        requireRange(settings.getIdleTimeoutMinutes(), 1, 1440, "无操作会话时长");
        requireRange(settings.getMaxSessionMinutes(), 1, 10080, "最大会话时长");
        requireRange(settings.getIdleUserDays(), 1, 3650, "闲置用户天数");
        if (settings.getIdleTimeoutMinutes() > settings.getMaxSessionMinutes()) {
            throw new BizException("无操作登录保持时间不能大于最大登录保持时间");
        }
        LoginType type = LoginType.of(settings.getDefaultLoginType());
        if (type != LoginType.PLATFORM_ADMIN && type != LoginType.PLATFORM_USER
                && type != LoginType.TENANT_ADMIN && type != LoginType.TENANT_USER) {
            throw new BizException("默认账号体系只允许平台管理员、平台用户、租户管理员、租户用户");
        }
        if (settings.getDefaultRoleId() != null && roleMapper.selectById(settings.getDefaultRoleId()) == null) {
            throw new BizException("默认角色不存在: " + settings.getDefaultRoleId());
        }
    }

    private void applyDynamicConfig(UserSettingsRecord settings) {
        DynamicConfigService service = configService.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            service.set("modality.security.password.min-length", String.valueOf(settings.getPasswordMinLength()));
            service.set("modality.security.password.require-classes",
                    String.valueOf(settings.getPasswordRequireClasses()));
            service.set("modality.security.password.max-age-days", String.valueOf(settings.getPasswordExpireDays()));
            service.set("modality.security.lockout.max-failures",
                    String.valueOf(settings.getLoginFailureThreshold()));
            service.set("modality.security.lockout.lock-minutes",
                    String.valueOf(settings.getLoginLockMinutes()));
            service.set("modality.security.session.active-timeout",
                    String.valueOf(settings.getIdleTimeoutMinutes() * 60L));
            long maxSessionSeconds = settings.getMaxSessionMinutes() * 60L;
            for (LoginType type : List.of(LoginType.PLATFORM_ADMIN, LoginType.PLATFORM_USER,
                    LoginType.TENANT_ADMIN, LoginType.TENANT_USER)) {
                service.set("modality.security.session.timeout-"
                        + type.value().toLowerCase(Locale.ROOT).replace('_', '-'),
                        String.valueOf(maxSessionSeconds));
            }
            service.set("modality.session.idle-timeout-seconds",
                    String.valueOf(settings.getIdleTimeoutMinutes() * 60L));
            service.set("modality.session.max-lifetime-seconds", String.valueOf(maxSessionSeconds));
        } catch (RuntimeException e) {
            log.warn("用户设置已保存，但动态安全配置同步失败：{}", e.getMessage());
        }
    }

    private void recordHistory(UserSettingsRecord before, UserSettingsRecord after, String operator, long now) {
        Map<String, Object> oldValues = groupedValues(before);
        Map<String, Object> newValues = groupedValues(after);
        for (String key : oldValues.keySet()) {
            Object oldValue = oldValues.get(key);
            Object newValue = newValues.get(key);
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            UserSettingsHistoryRecord history = new UserSettingsHistoryRecord();
            history.setSettingKey(key);
            history.setBeforeJson(JsonUtils.toJson(oldValue));
            history.setAfterJson(JsonUtils.toJson(newValue));
            history.setOperator(operator);
            history.setCreateTime(now);
            historyMapper.insert(history);
        }
    }

    private Map<String, Object> groupedValues(UserSettingsRecord settings) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("password", Map.of(
                "minLength", settings.getPasswordMinLength(),
                "requireClasses", settings.getPasswordRequireClasses(),
                "expireDays", settings.getPasswordExpireDays(),
                "allowSelfReset", settings.getPasswordAllowSelfReset()));
        values.put("defaultAccountType", settings.getDefaultLoginType());
        values.put("defaultRole", settings.getDefaultRoleId());
        values.put("initialPasswordEmail", settings.getSendInitialPasswordEmail());
        values.put("forceChangePassword", settings.getForceChangePassword());
        values.put("loginFailureLock", Map.of(
                "threshold", settings.getLoginFailureThreshold(),
                "lockMinutes", settings.getLoginLockMinutes()));
        values.put("session", Map.of(
                "idleTimeoutMinutes", settings.getIdleTimeoutMinutes(),
                "maxSessionMinutes", settings.getMaxSessionMinutes()));
        values.put("idleRestriction", Map.of(
                "enabled", settings.getRestrictIdleUsers(),
                "idleUserDays", settings.getIdleUserDays()));
        return values;
    }

    private UserSettingsRecord defaults() {
        UserSettingsRecord settings = new UserSettingsRecord();
        settings.setPasswordMinLength(8);
        settings.setPasswordRequireClasses(3);
        settings.setPasswordExpireDays(90);
        settings.setPasswordAllowSelfReset(false);
        settings.setDefaultLoginType(LoginType.TENANT_USER.value());
        settings.setSendInitialPasswordEmail(true);
        settings.setForceChangePassword(true);
        settings.setLoginFailureThreshold(5);
        settings.setLoginLockMinutes(30);
        settings.setIdleTimeoutMinutes(30);
        settings.setMaxSessionMinutes(120);
        settings.setRestrictIdleUsers(false);
        settings.setIdleUserDays(90);
        return settings;
    }

    private UserSettingsRecord copy(UserSettingsRecord source) {
        UserSettingsRecord target = new UserSettingsRecord();
        target.setId(source.getId());
        target.setPasswordMinLength(source.getPasswordMinLength());
        target.setPasswordRequireClasses(source.getPasswordRequireClasses());
        target.setPasswordExpireDays(source.getPasswordExpireDays());
        target.setPasswordAllowSelfReset(source.getPasswordAllowSelfReset());
        target.setDefaultLoginType(source.getDefaultLoginType());
        target.setDefaultRoleId(source.getDefaultRoleId());
        target.setSendInitialPasswordEmail(source.getSendInitialPasswordEmail());
        target.setForceChangePassword(source.getForceChangePassword());
        target.setLoginFailureThreshold(source.getLoginFailureThreshold());
        target.setLoginLockMinutes(source.getLoginLockMinutes());
        target.setIdleTimeoutMinutes(source.getIdleTimeoutMinutes());
        target.setMaxSessionMinutes(source.getMaxSessionMinutes());
        target.setRestrictIdleUsers(source.getRestrictIdleUsers());
        target.setIdleUserDays(source.getIdleUserDays());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }

    private void requireRange(Integer value, int min, int max, String label) {
        if (value == null || value < min || value > max) {
            throw new BizException(label + "必须在 " + min + " 到 " + max + " 之间");
        }
    }
}
