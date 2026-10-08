package cn.slainc.security.login;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.LoginEvent;
import cn.slainc.core.exception.BizException;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.security.LoginTypes;
import cn.slainc.security.StpKit;
import cn.slainc.security.audit.SecurityAuditRecorder;
import cn.slainc.security.ban.AccountBanService;
import cn.slainc.security.config.DeviceOverflowMode;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.config.SecurityKeys;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.lock.AccountLockService;
import cn.slainc.security.metrics.SecurityMetricsPort;
import cn.slainc.security.password.PasswordPolicyService;
import cn.slainc.security.restrict.LoginRestrictionService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SecurityLoginService {

    public static final String DEFAULT_DEVICE_TYPE = "default";

    private static final Logger log = LoggerFactory.getLogger(SecurityLoginService.class);

    private final SecurityConfigReader config;

    private final ObjectProvider<AccountAuthProvider> authProvider;

    private final AccountBanService banService;

    private final AccountLockService lockService;

    private final LoginRestrictionService restrictionService;

    private final PasswordPolicyService passwordPolicy;

    private final KickService kickService;

    private final SecurityAuditRecorder recorder;

    private final SecurityMetricsPort metrics;

    private final ApplicationEventPublisher publisher;

    private final CurrentRequestReader requestReader;

    private final StringRedisTemplate redis;

    public SecurityLoginService(SecurityConfigReader config, ObjectProvider<AccountAuthProvider> authProvider,
                                AccountBanService banService, AccountLockService lockService,
                                LoginRestrictionService restrictionService, PasswordPolicyService passwordPolicy,
                                KickService kickService, SecurityAuditRecorder recorder, SecurityMetricsPort metrics,
                                ApplicationEventPublisher publisher, CurrentRequestReader requestReader,
                                StringRedisTemplate redis) {
        this.config = config;
        this.authProvider = authProvider;
        this.banService = banService;
        this.lockService = lockService;
        this.restrictionService = restrictionService;
        this.passwordPolicy = passwordPolicy;
        this.kickService = kickService;
        this.recorder = recorder;
        this.metrics = metrics;
        this.publisher = publisher;
        this.requestReader = requestReader;
        this.redis = redis;
    }

    public LoginResult login(LoginType type, LoginRequest request) {
        String clientIp = request.clientIp() != null && !request.clientIp().isBlank()
                ? request.clientIp() : requestReader.clientIp();
        String userAgent = requestReader.userAgent();
        String deviceType = request.deviceType() == null || request.deviceType().isBlank()
                ? DEFAULT_DEVICE_TYPE : request.deviceType();
        try {
            restrictionService.check(type, clientIp, deviceType);
            AccountAuthProvider provider = authProvider.getIfAvailable();
            if (provider == null) {
                throw new BizException("未接入账号数据源，无法登录");
            }
            PasswordAccount account = provider.findAccount(request.username(), type);
            if (account == null) {
                return fail(request, type, clientIp, credentialFailureMessage(lockService.recordFailure(type,
                        request.username())));
            }
            Object loginId = account.userId() == null ? account.username() : account.userId();
            if (!account.enabled()) {
                return fail(request, type, clientIp, "账号已被禁用，禁止登录");
            }
            if (banService.isBanned(type, loginId)) {
                long remain = Math.max(banService.getBanRemain(type, loginId), 0);
                return fail(request, type, clientIp, "账号已被封禁，剩余 " + remain + " 秒");
            }
            if (lockService.isLocked(type, loginId)) {
                long remain = lockService.lockRemainSeconds(type, loginId);
                return fail(request, type, clientIp, "密码错误次数过多，账号已锁定，请于 " + remain + " 秒后重试");
            }
            checkDeviceLimitBeforeLogin(type, loginId, deviceType);
            if (request.password() == null || !BCryptUtils.verifyQuietly(request.password(), account.passwordHash())) {
                return fail(request, type, clientIp,
                        credentialFailureMessage(lockService.recordFailure(type, loginId)));
            }
            String deviceId = resolveDeviceId(request);
            SaLoginParameter parameter = buildParameter(type, request, account, clientIp, userAgent, deviceType,
                    deviceId);
            StpKit.of(type).login(loginId, parameter);
            bindSessionTenant(type);
            checkDeviceOverflowAfterLogin(type, loginId, deviceType);
            checkTravelAlarm(type, loginId, clientIp);
            lockService.reset(type, loginId);
            SaTokenInfo info = StpKit.of(type).getTokenInfo();
            boolean mustChangePassword = account.mustChangePassword() || passwordPolicy.isExpired(account);
            publisher.publishEvent(LoginEvent.success(account.userId(), account.username(), type, clientIp));
            metrics.login(type.value(), true);
            return new LoginResult(info.tokenName, info.tokenValue,
                    account.userId() == null ? null : account.userId(), type.value(), info.tokenTimeout,
                    mustChangePassword);
        } catch (BizException e) {
            publisher.publishEvent(LoginEvent.failure(request.username(), type, clientIp, e.getMessage()));
            metrics.login(type.value(), false);
            throw e;
        }
    }

    private static String credentialFailureMessage(int remaining) {
        return remaining <= 0 ? "用户名或密码错误，账号已锁定" : "用户名或密码错误，剩余尝试次数 " + remaining;
    }

    private void bindSessionTenant(LoginType type) {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            return;
        }
        try {
            StpKit.of(type).getSession().set(TenantContext.SESSION_ATTRIBUTE, tenantId);
        } catch (RuntimeException exception) {
            log.warn("写入会话租户失败：{}", exception.getMessage());
        }
    }

    private LoginResult fail(LoginRequest request, LoginType type, String clientIp, String reason) {
        publisher.publishEvent(LoginEvent.failure(request.username(), type, clientIp, reason));
        metrics.login(type.value(), false);
        throw new BizException(reason);
    }

    private SaLoginParameter buildParameter(LoginType type, LoginRequest request, PasswordAccount account,
                                            String clientIp, String userAgent, String deviceType, String deviceId) {
        SaLoginParameter parameter = new SaLoginParameter();
        parameter.setDeviceType(deviceType);
        parameter.setDeviceId(deviceId);
        parameter.setExtra("username", account.username());
        if (TenantContext.get() != null) {
            parameter.setExtra("tenantId", TenantContext.get());
            // 账号级 extra 在默认 StpLogic 下不会落库，租户还必须写进 terminal extraData 才能被会话查询与按租户统计读到
            parameter.setTerminalExtra("tenantId", TenantContext.get());
        }
        parameter.setTerminalExtra("ip", clientIp == null ? "" : clientIp);
        parameter.setTerminalExtra("ua", userAgent == null ? "" : userAgent);
        parameter.setTerminalExtra("loginTime", System.currentTimeMillis());
        parameter.setTerminalExtra("deviceId", deviceId == null ? "" : deviceId);
        parameter.setTerminalExtra("username", account.username());
        parameter.setTimeout(config.sessionTimeout(type));
        parameter.setActiveTimeout(config.sessionActiveTimeout());
        parameter.setIsConcurrent(!config.deviceSingleSessionPerType());
        parameter.setIsShare(false);
        parameter.setMaxLoginCount(-1);
        return parameter;
    }

    private String resolveDeviceId(LoginRequest request) {
        if (request.deviceId() != null && !request.deviceId().isBlank()) {
            return request.deviceId();
        }
        try {
            return requestReader.header(config.deviceIdHeader());
        } catch (Exception e) {
            return null;
        }
    }

    private void checkDeviceLimitBeforeLogin(LoginType type, Object loginId, String deviceType) {
        if (config.deviceOverflowMode() != DeviceOverflowMode.REJECT) {
            return;
        }
        int max = config.deviceMaxCountPerType();
        if (max <= 0) {
            return;
        }
        List<SaTerminalInfo> terminals = StpKit.of(type).getTerminalListByLoginId(loginId, deviceType);
        if (terminals != null && terminals.size() >= max) {
            throw new BizException("该账号在当前设备类型的登录数量已达上限（" + max + "），请先下线其它设备");
        }
    }

    private void checkDeviceOverflowAfterLogin(LoginType type, Object loginId, String deviceType) {
        if (config.deviceOverflowMode() != DeviceOverflowMode.KICK_OLDEST) {
            return;
        }
        int max = config.deviceMaxCountPerType();
        if (max <= 0) {
            return;
        }
        while (true) {
            List<SaTerminalInfo> terminals = StpKit.of(type).getTerminalListByLoginId(loginId, deviceType);
            if (terminals == null || terminals.size() <= max) {
                return;
            }
            SaTerminalInfo oldest = terminals.stream()
                    .min(Comparator.comparingLong(SaTerminalInfo::getCreateTime))
                    .orElseThrow(() -> new BizException("设备登录数量溢出，但未找到可踢出的最早会话"));
            kickService.kickout(type, loginId, deviceType, oldest.getTokenValue(), "设备登录数量超限，自动踢出最早登录");
        }
    }

    private void checkTravelAlarm(LoginType type, Object loginId, String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        String key = SecurityKeys.lastIpKey(type.value(), loginId);
        String previous = null;
        try {
            previous = redis.opsForValue().get(key);
            redis.opsForValue().set(key, clientIp, Duration.ofSeconds(config.sessionTimeout(type)));
        } catch (Exception e) {
            log.warn("记录最后登录 IP 失败：{}", e.getMessage());
        }
        if (previous != null && !previous.isBlank() && !previous.equals(clientIp)) {
            recorder.alarm("异地登录", type, loginId, clientIp,
                    "登录 IP 发生变化：" + previous + " -> " + clientIp);
        }
    }

    public void logout(LoginType type) {
        StpKit.of(type).logout();
    }

    public void logout(LoginType type, Object loginId) {
        StpKit.of(type).logout(loginId);
        recorder.logout(asLong(loginId), null, type, true);
    }

    public void checkLogin(LoginType type) {
        StpKit.of(type).checkLogin();
    }

    public Object getLoginId(LoginType type) {
        return StpKit.of(type).getLoginId();
    }

    public SaTokenInfo getTokenInfo(LoginType type) {
        return StpKit.of(type).getTokenInfo();
    }

    public List<Map<String, Object>> onlineTerminals(LoginType type, Object loginId) {
        List<SaTerminalInfo> terminals = StpKit.of(type).getTerminalListByLoginId(loginId);
        return terminals.stream().map(terminal -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tokenValue", terminal.getTokenValue());
            item.put("deviceType", terminal.getDeviceType());
            item.put("deviceId", terminal.getDeviceId());
            item.put("createTime", terminal.getCreateTime());
            item.put("extraData", terminal.getExtraData());
            return item;
        }).collect(Collectors.toList());
    }

    private Long asLong(Object loginId) {
        if (loginId instanceof Long value) {
            return value;
        }
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        if (loginId != null) {
            try {
                return Long.parseLong(loginId.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
