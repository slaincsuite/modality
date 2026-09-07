package cn.slainc.security.spi;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityProperties;

import java.time.LocalDateTime;
import java.util.List;

public class ConfigAccountAuthProvider implements AccountAuthProvider {

    private final SecurityProperties properties;

    public ConfigAccountAuthProvider(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public PasswordAccount findAccount(String username, LoginType type) {
        if (username == null || type == null) {
            return null;
        }
        List<SecurityProperties.AccountItem> accounts = properties.getAccounts();
        for (SecurityProperties.AccountItem item : accounts) {
            if (!username.equals(item.getUsername())) {
                continue;
            }
            LoginType itemType = LoginType.of(item.getType());
            if (itemType != type) {
                continue;
            }
            return new PasswordAccount(item.getUserId(), item.getUsername(), item.getPasswordBcrypt(),
                    item.isEnabled(), null, item.isMustChangePassword());
        }
        return null;
    }

    @Override
    public PasswordAccount findAccountByUserId(Long userId, LoginType type) {
        if (userId == null || type == null) {
            return null;
        }
        for (SecurityProperties.AccountItem item : properties.getAccounts()) {
            if (!userId.equals(item.getUserId()) || LoginType.of(item.getType()) != type) {
                continue;
            }
            return new PasswordAccount(item.getUserId(), item.getUsername(), item.getPasswordBcrypt(),
                    item.isEnabled(), null, item.isMustChangePassword());
        }
        return null;
    }

    public static PasswordAccount toAccount(SecurityProperties.AccountItem item) {
        return new PasswordAccount(item.getUserId(), item.getUsername(), item.getPasswordBcrypt(),
                item.isEnabled(), LocalDateTime.now(), item.isMustChangePassword());
    }
}
