package cn.slainc.modules.auth.service.login;

import cn.slainc.modules.auth.service.mfa.AuthSecondFactorGate;

import cn.slainc.captcha.CaptchaInput;
import cn.slainc.captcha.CaptchaService;
import cn.slainc.captcha.CaptchaVerifyResult;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.LoginEvent;
import cn.slainc.core.exception.BizException;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.modules.auth.service.policy.SecurityPolicyService;
import cn.slainc.security.ban.AccountBanService;
import cn.slainc.security.lock.AccountLockService;
import cn.slainc.security.login.CurrentRequestReader;
import cn.slainc.security.login.LoginRequest;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.AccountRestrictionPort;
import cn.slainc.security.spi.PasswordAccount;
import cn.slainc.session.extend.SessionExtender;
import cn.slainc.session.extend.SessionRenewalService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

public class AuthLoginService {

    private final SecurityLoginService delegate;

    private final AuthProperties properties;

    private final ObjectProvider<CaptchaService> captchaService;

    private final ObjectProvider<AccountRestrictionPort> restrictionPort;

    private final ObjectProvider<SessionRenewalService> renewalService;

    private final ObjectProvider<SessionExtender> sessionExtender;

    private final ObjectProvider<CurrentRequestReader> requestReader;

    private final ApplicationEventPublisher publisher;

    private final ObjectProvider<AuthSecondFactorGate> secondFactorGate;

    private final ObjectProvider<AccountAuthProvider> authProvider;

    private final ObjectProvider<AccountBanService> banService;

    private final ObjectProvider<AccountLockService> lockService;

    private final ObjectProvider<SecurityPolicyService> policyService;

    public AuthLoginService(SecurityLoginService delegate, AuthProperties properties,
                            ObjectProvider<CaptchaService> captchaService,
                            ObjectProvider<AccountRestrictionPort> restrictionPort,
                            ObjectProvider<SessionRenewalService> renewalService,
                            ObjectProvider<SessionExtender> sessionExtender,
                            ObjectProvider<CurrentRequestReader> requestReader,
                            ApplicationEventPublisher publisher,
                            ObjectProvider<AuthSecondFactorGate> secondFactorGate,
                            ObjectProvider<AccountAuthProvider> authProvider,
                            ObjectProvider<AccountBanService> banService,
                            ObjectProvider<AccountLockService> lockService,
                            ObjectProvider<SecurityPolicyService> policyService) {
        this.delegate = delegate;
        this.properties = properties;
        this.captchaService = captchaService;
        this.restrictionPort = restrictionPort;
        this.renewalService = renewalService;
        this.sessionExtender = sessionExtender;
        this.requestReader = requestReader;
        this.publisher = publisher;
        this.secondFactorGate = secondFactorGate;
        this.authProvider = authProvider;
        this.banService = banService;
        this.lockService = lockService;
        this.policyService = policyService;
    }

    public record LoginCommand(String username, String password, String deviceType, String deviceId,
                               String captchaId, String captchaCode, boolean remember) {
    }

    public record AuthLoginView(String tokenName, String tokenValue, Long loginId, String loginType,
                                long tokenTimeout, boolean mustChangePassword, boolean remember,
                                boolean secondFactorRequired, String challengeId, String method) {

        public static AuthLoginView of(LoginResult result, boolean remember, long tokenTimeout) {
            return new AuthLoginView(result.tokenName(), result.tokenValue(), result.loginId(), result.loginType(),
                    tokenTimeout, result.mustChangePassword(), remember, false, null, null);
        }

        public static AuthLoginView secondFactorRequired(String challengeId, String method, Long loginId,
                                                          String loginType) {
            return new AuthLoginView(null, null, loginId, loginType, 0, false, false, true, challengeId, method);
        }
    }

    public AuthLoginView login(LoginType type, LoginCommand command) {
        CurrentRequestReader reader = requestReader.getIfAvailable();
        String clientIp = reader == null ? null : reader.clientIp();
        checkCaptcha(type, command, clientIp);
        checkUserRestrictions(type, command, clientIp);
        AuthSecondFactorGate gate = secondFactorGate == null ? null : secondFactorGate.getIfAvailable();
        if (gate != null && gate.appliesTo(type)) {
            PasswordAccount account = findAccount(type, command.username());
            if (account != null && gate.required(type, account.userId(), account.username())) {
                requireTenant(type, account);
                verifyPassword(type, command, account, clientIp);
                String challengeId = gate.challenge(type, account.userId(), account.username(),
                        command.deviceType(), command.deviceId(), command.remember());
                return AuthLoginView.secondFactorRequired(challengeId, gate.method(), account.userId(), type.value());
            }
        }
        PasswordAccount account = findAccount(type, command.username());
        if (account != null) {
            SecurityPolicyService policy = policyService == null ? null : policyService.getIfAvailable();
            if (policy != null) {
                policy.alignTravelBaseline(type, account.userId(), clientIp);
            }
        }
        LoginResult result = delegate.login(type, new LoginRequest(command.username(), command.password(),
                command.deviceType(), command.deviceId(), clientIp));
        return completeLogin(type, result, command.remember());
    }

    public AuthLoginView completeLogin(LoginType type, LoginResult result, boolean remember) {
        if (remember) {
            SessionRenewalService renewal = renewalService.getIfAvailable();
            SessionExtender extender = sessionExtender.getIfAvailable();
            if (renewal != null) {
                renewal.renew(type, result.tokenValue(), properties.getRememberMe().getTtlSeconds());
                if (extender != null) {
                    extender.setExtra(type, result.tokenValue(), "remember", true);
                }
                return AuthLoginView.of(result, true, properties.getRememberMe().getTtlSeconds());
            }
        }
        return AuthLoginView.of(result, false, result.tokenTimeout());
    }

    private PasswordAccount findAccount(LoginType type, String username) {
        AccountAuthProvider provider = authProvider == null ? null : authProvider.getIfAvailable();
        if (provider == null || username == null) {
            return null;
        }
        try {
            return provider.findAccount(username, type);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void requireTenant(LoginType type, PasswordAccount account) {
        Long selected = TenantContext.get();
        if (selected != null && type != LoginType.PLATFORM_ADMIN && account.tenantId() != null
                && !selected.equals(account.tenantId())) {
            throw new BizException("账号不属于所选租户，请重新选择租户");
        }
    }

    // MFA 分支在签发挑战前就地完成密码裁决：封禁、锁定、失败计数与事件口径与 security 密码登录保持一致
    private void verifyPassword(LoginType type, LoginCommand command, PasswordAccount account, String clientIp) {
        Object loginId = account.userId() == null ? account.username() : account.userId();
        if (!account.enabled()) {
            throw failure(type, command.username(), clientIp, "账号已被禁用，禁止登录");
        }
        AccountBanService ban = banService == null ? null : banService.getIfAvailable();
        if (ban != null && ban.isBanned(type, loginId)) {
            long remain = Math.max(ban.getBanRemain(type, loginId), 0);
            throw failure(type, command.username(), clientIp, "账号已被封禁，剩余 " + remain + " 秒");
        }
        AccountLockService lock = lockService == null ? null : lockService.getIfAvailable();
        if (lock != null && lock.isLocked(type, loginId)) {
            long remain = lock.lockRemainSeconds(type, loginId);
            throw failure(type, command.username(), clientIp, "密码错误次数过多，账号已锁定，请于 " + remain + " 秒后重试");
        }
        if (command.password() == null || !BCryptUtils.verifyQuietly(command.password(), account.passwordHash())) {
            int remaining = lock == null ? -1 : lock.recordFailure(type, loginId);
            throw failure(type, command.username(), clientIp, credentialFailureMessage(remaining));
        }
        if (lock != null) {
            lock.reset(type, loginId);
        }
    }

    private String credentialFailureMessage(int remaining) {
        if (remaining < 0) {
            return "账号或密码错误";
        }
        return remaining <= 0 ? "账号或密码错误，账号已锁定" : "账号或密码错误，剩余尝试次数 " + remaining;
    }

    private void checkCaptcha(LoginType type, LoginCommand command, String clientIp) {
        if (!properties.getCaptcha().isEnabled()) {
            return;
        }
        CaptchaService captcha = captchaService.getIfAvailable();
        if (captcha == null) {
            throw failure(type, command.username(), clientIp, "已开启验证码校验，但验证码服务不可用");
        }
        if (command.captchaId() == null || command.captchaId().isBlank()
                || command.captchaCode() == null || command.captchaCode().isBlank()) {
            throw failure(type, command.username(), clientIp, "请输入验证码");
        }
        CaptchaVerifyResult verify = captcha.verify(properties.getCaptcha().getChannel(), command.captchaId(),
                CaptchaInput.ofCode(command.captchaCode()));
        if (!verify.success()) {
            throw failure(type, command.username(), clientIp, "验证码校验失败：" + verify.message());
        }
    }

    // 用户级登录限制（IP/时段/设备）在交给 security 之前先行裁决，这是批次 8 预留的强制执行接入点
    private void checkUserRestrictions(LoginType type, LoginCommand command, String clientIp) {
        AccountRestrictionPort port = restrictionPort.getIfAvailable();
        if (port == null) {
            return;
        }
        List<String> violations = port.validate(command.username(), type, clientIp, command.deviceType());
        if (violations != null && !violations.isEmpty()) {
            throw failure(type, command.username(), clientIp, String.join("；", violations));
        }
    }

    private BizException failure(LoginType type, String username, String clientIp, String reason) {
        publisher.publishEvent(LoginEvent.failure(username, type, clientIp, reason));
        return new BizException(reason);
    }
}
