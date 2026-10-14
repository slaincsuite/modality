package cn.slainc.modules.auth.service.session;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.LoginEvent;
import cn.slainc.core.exception.BizException;
import cn.slainc.monitor.metrics.BusinessMetrics;
import cn.slainc.security.StpKit;
import cn.slainc.security.audit.SecurityAuditRecorder;
import cn.slainc.security.ban.AccountBanService;
import cn.slainc.security.config.DeviceOverflowMode;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.config.SecurityKeys;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.login.CurrentRequestReader;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.password.PasswordPolicyService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.AccountRestrictionPort;
import cn.slainc.security.spi.PasswordAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

// 扫码确认、第三方回调这类「身份已在外部证明」的登录走这里建立本系统会话，参数与 security 的密码登录保持同构
public class AuthSessionService {

    public static final String DEFAULT_DEVICE_TYPE = "default";

    private static final Logger log = LoggerFactory.getLogger(AuthSessionService.class);

    private final SecurityConfigReader config;

    private final ObjectProvider<AccountAuthProvider> authProvider;

    private final AccountBanService banService;

    private final KickService kickService;

    private final SecurityAuditRecorder recorder;

    private final ObjectProvider<PasswordPolicyService> passwordPolicy;

    private final ApplicationEventPublisher publisher;

    private final CurrentRequestReader requestReader;

    private final ObjectProvider<StringRedisTemplate> redis;

    private final ObjectProvider<BusinessMetrics> metrics;

    private final ObjectProvider<AccountRestrictionPort> restrictionPort;

    public AuthSessionService(SecurityConfigReader config, ObjectProvider<AccountAuthProvider> authProvider,
                              AccountBanService banService, KickService kickService, SecurityAuditRecorder recorder,
                              ObjectProvider<PasswordPolicyService> passwordPolicy,
                              ApplicationEventPublisher publisher, CurrentRequestReader requestReader,
                              ObjectProvider<StringRedisTemplate> redis, ObjectProvider<BusinessMetrics> metrics) {
        this(config, authProvider, banService, kickService, recorder, passwordPolicy, publisher, requestReader, redis,
                metrics, null);
    }

    public AuthSessionService(SecurityConfigReader config, ObjectProvider<AccountAuthProvider> authProvider,
                              AccountBanService banService, KickService kickService, SecurityAuditRecorder recorder,
                              ObjectProvider<PasswordPolicyService> passwordPolicy,
                              ApplicationEventPublisher publisher, CurrentRequestReader requestReader,
                              ObjectProvider<StringRedisTemplate> redis, ObjectProvider<BusinessMetrics> metrics,
                              ObjectProvider<AccountRestrictionPort> restrictionPort) {
        this.config = config;
        this.authProvider = authProvider;
        this.banService = banService;
        this.kickService = kickService;
        this.recorder = recorder;
        this.passwordPolicy = passwordPolicy;
        this.publisher = publisher;
        this.requestReader = requestReader;
        this.redis = redis;
        this.metrics = metrics;
        this.restrictionPort = restrictionPort;
    }

    public LoginResult establish(LoginType type, Object loginId, String deviceType, String deviceId) {
        String clientIp = requestReader.clientIp();
        String userAgent = requestReader.userAgent();
        String resolvedDeviceType = deviceType == null || deviceType.isBlank() ? DEFAULT_DEVICE_TYPE : deviceType;
        try {
            AccountAuthProvider provider = authProvider.getIfAvailable();
            if (provider == null) {
                throw new BizException("未接入账号数据源，无法建立会话");
            }
            Long userId = asLong(loginId);
            PasswordAccount account = userId == null ? null : provider.findAccountByUserId(userId, type);
            if (account == null) {
                throw new BizException("账号不存在，无法建立会话");
            }
            if (!account.enabled()) {
                throw new BizException("账号已被禁用，禁止登录");
            }
            if (banService.isBanned(type, loginId)) {
                throw new BizException("账号已被封禁，禁止登录");
            }
            checkUserRestrictions(type, account, clientIp, resolvedDeviceType);
            checkDeviceLimitBeforeLogin(type, loginId, resolvedDeviceType);
            SaLoginParameter parameter = buildParameter(type, account, clientIp, userAgent, resolvedDeviceType,
                    deviceId);
            StpKit.of(type).login(loginId, parameter);
            bindSessionTenant(type);
            checkDeviceOverflowAfterLogin(type, loginId, resolvedDeviceType);
            checkTravelAlarm(type, loginId, clientIp);
            SaTokenInfo info = StpKit.of(type).getTokenInfo();
            PasswordPolicyService policy = passwordPolicy.getIfAvailable();
            boolean mustChangePassword = account.mustChangePassword()
                    || (policy != null && policy.isExpired(account));
            publisher.publishEvent(LoginEvent.success(account.userId(), account.username(), type, clientIp));
            BusinessMetrics businessMetrics = metrics.getIfAvailable();
            if (businessMetrics != null) {
                businessMetrics.loginSuccess(type.value());
            }
            return new LoginResult(info.tokenName, info.tokenValue, account.userId(), type.value(),
                    info.tokenTimeout, mustChangePassword);
        } catch (BizException e) {
            publisher.publishEvent(LoginEvent.failure(String.valueOf(loginId), type, clientIp, e.getMessage()));
            BusinessMetrics businessMetrics = metrics.getIfAvailable();
            if (businessMetrics != null) {
                businessMetrics.loginFailure(type.value(), e.getMessage());
            }
            throw e;
        }
    }

    // 免密链路（扫码/第三方回调/绑定）此前绕过了用户级登录限制，这里与密码登录做同样的裁决
    private void checkUserRestrictions(LoginType type, PasswordAccount account, String clientIp, String deviceType) {
        AccountRestrictionPort port = restrictionPort == null ? null : restrictionPort.getIfAvailable();
        if (port == null) {
            return;
        }
        List<String> violations = port.validate(account.username(), type, clientIp, deviceType);
        if (violations != null && !violations.isEmpty()) {
            throw new BizException(String.join("；", violations));
        }
    }

    private void bindSessionTenant(LoginType type) {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            return;
        }
        StpKit.of(type).getSession().set(TenantContext.SESSION_ATTRIBUTE, tenantId);
    }

    private SaLoginParameter buildParameter(LoginType type, PasswordAccount account, String clientIp,
                                            String userAgent, String deviceType, String deviceId) {
        SaLoginParameter parameter = new SaLoginParameter();
        parameter.setDeviceType(deviceType);
        parameter.setDeviceId(deviceId);
        parameter.setExtra("username", account.username());
        if (TenantContext.get() != null) {
            parameter.setExtra("tenantId", TenantContext.get());
            parameter.setTerminalExtra("tenantId", TenantContext.get());
        }
        parameter.setTerminalExtra("ip", clientIp == null ? "" : clientIp);
        parameter.setTerminalExtra("ua", userAgent == null ? "" : userAgent);
        parameter.setTerminalExtra("loginTime", System.currentTimeMillis());
        parameter.setTerminalExtra("deviceId", deviceId == null ? "" : deviceId);
        parameter.setTerminalExtra("username", account.username());
        parameter.setTimeout(config.sessionTimeout(type));
        parameter.setIsConcurrent(!config.deviceSingleSessionPerType());
        parameter.setIsShare(false);
        parameter.setMaxLoginCount(-1);
        return parameter;
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
        StringRedisTemplate template = redis.getIfAvailable();
        if (template == null) {
            return;
        }
        String key = SecurityKeys.lastIpKey(type.value(), loginId);
        String previous = null;
        try {
            previous = template.opsForValue().get(key);
            template.opsForValue().set(key, clientIp, Duration.ofSeconds(config.sessionTimeout(type)));
        } catch (Exception e) {
            log.warn("记录最后登录 IP 失败：{}", e.getMessage());
        }
        if (previous != null && !previous.isBlank() && !previous.equals(clientIp)) {
            recorder.alarm("异地登录", type, loginId, clientIp,
                    "登录 IP 发生变化：" + previous + " -> " + clientIp);
        }
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
