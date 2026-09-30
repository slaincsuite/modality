package cn.slainc.modules.auth.service;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.service.UserPasswordService;
import cn.slainc.security.password.PasswordPolicyService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import org.springframework.beans.factory.ObjectProvider;

import java.time.ZoneId;

public class AuthPasswordService {

    private final ObjectProvider<UserPasswordService> upmsPasswordService;

    private final PasswordPolicyService passwordPolicy;

    private final ObjectProvider<AccountAuthProvider> authProvider;

    public AuthPasswordService(ObjectProvider<UserPasswordService> upmsPasswordService,
                               PasswordPolicyService passwordPolicy,
                               ObjectProvider<AccountAuthProvider> authProvider) {
        this.upmsPasswordService = upmsPasswordService;
        this.passwordPolicy = passwordPolicy;
        this.authProvider = authProvider;
    }

    public void change(LoginType type, Long userId, String oldPassword, String newPassword) {
        UserPasswordService upms = upmsPasswordService.getIfAvailable();
        if (upms != null) {
            upms.changeBySelf(userId, type, oldPassword, newPassword);
            return;
        }
        passwordPolicy.changePassword(type, userId, oldPassword, newPassword, true);
    }

    public record PasswordStatusView(boolean mustChangePassword, boolean expired, Long passwordChangedAtMillis) {
    }

    public PasswordStatusView status(LoginType type, Long userId) {
        UserPasswordService upms = upmsPasswordService.getIfAvailable();
        PasswordAccount account = accountOf(type, userId);
        boolean must = upms != null ? upms.mustChangePassword(userId, type)
                : account != null && (account.mustChangePassword()
                        || passwordPolicy.isExpired(account));
        boolean expired = account != null && passwordPolicy.isExpired(account);
        Long changedAt = account == null || account.passwordChangedAt() == null ? null
                : account.passwordChangedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new PasswordStatusView(must, expired, changedAt);
    }

    private PasswordAccount accountOf(LoginType type, Long userId) {
        AccountAuthProvider provider = authProvider.getIfAvailable();
        return provider == null ? null : provider.findAccountByUserId(userId, type);
    }
}
