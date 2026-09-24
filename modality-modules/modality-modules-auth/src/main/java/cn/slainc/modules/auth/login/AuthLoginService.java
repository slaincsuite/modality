package cn.slainc.modules.auth.login;

import cn.slainc.captcha.CaptchaInput;
import cn.slainc.captcha.CaptchaService;
import cn.slainc.captcha.CaptchaVerifyResult;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.LoginEvent;
import cn.slainc.core.exception.BizException;
import cn.slainc.modules.auth.config.AuthProperties;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.UserService;
import cn.slainc.security.login.CurrentRequestReader;
import cn.slainc.security.login.LoginRequest;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.session.extend.SessionExtender;
import cn.slainc.session.extend.SessionRenewalService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

public class AuthLoginService {

    private final SecurityLoginService delegate;

    private final AuthProperties properties;

    private final ObjectProvider<CaptchaService> captchaService;

    private final ObjectProvider<UserService> userService;

    private final ObjectProvider<SessionRenewalService> renewalService;

    private final ObjectProvider<SessionExtender> sessionExtender;

    private final ObjectProvider<CurrentRequestReader> requestReader;

    private final ApplicationEventPublisher publisher;

    public AuthLoginService(SecurityLoginService delegate, AuthProperties properties,
                            ObjectProvider<CaptchaService> captchaService, ObjectProvider<UserService> userService,
                            ObjectProvider<SessionRenewalService> renewalService,
                            ObjectProvider<SessionExtender> sessionExtender,
                            ObjectProvider<CurrentRequestReader> requestReader,
                            ApplicationEventPublisher publisher) {
        this.delegate = delegate;
        this.properties = properties;
        this.captchaService = captchaService;
        this.userService = userService;
        this.renewalService = renewalService;
        this.sessionExtender = sessionExtender;
        this.requestReader = requestReader;
        this.publisher = publisher;
    }

    public record LoginCommand(String username, String password, String deviceType, String deviceId,
                               String captchaId, String captchaCode, boolean remember) {
    }

    public record AuthLoginView(String tokenName, String tokenValue, Long loginId, String loginType,
                                long tokenTimeout, boolean mustChangePassword, boolean remember) {

        public static AuthLoginView of(LoginResult result, boolean remember, long tokenTimeout) {
            return new AuthLoginView(result.tokenName(), result.tokenValue(), result.loginId(), result.loginType(),
                    tokenTimeout, result.mustChangePassword(), remember);
        }
    }

    public AuthLoginView login(LoginType type, LoginCommand command) {
        CurrentRequestReader reader = requestReader.getIfAvailable();
        String clientIp = reader == null ? null : reader.clientIp();
        checkCaptcha(type, command, clientIp);
        checkUserRestrictions(type, command, clientIp);
        LoginResult result = delegate.login(type, new LoginRequest(command.username(), command.password(),
                command.deviceType(), command.deviceId(), clientIp));
        if (command.remember()) {
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
        UserService service = userService.getIfAvailable();
        if (service == null) {
            return;
        }
        UserRecord user = service.findByUsername(command.username(), type);
        if (user == null) {
            return;
        }
        List<String> violations = service.validateRestrictions(user, clientIp, command.deviceType());
        if (violations != null && !violations.isEmpty()) {
            throw failure(type, command.username(), clientIp, String.join("；", violations));
        }
    }

    private BizException failure(LoginType type, String username, String clientIp, String reason) {
        publisher.publishEvent(LoginEvent.failure(username, type, clientIp, reason));
        return new BizException(reason);
    }
}
