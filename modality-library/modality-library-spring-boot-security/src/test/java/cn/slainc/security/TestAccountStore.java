package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.AccountPasswordUpdater;
import cn.slainc.security.spi.PasswordAccount;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestAccountStore implements AccountAuthProvider, AccountPasswordUpdater {

    public static final String DEFAULT_PASSWORD = "Password#123";

    private final Map<String, MutableAccount> byUsername = new ConcurrentHashMap<>();

    public TestAccountStore() {
        register("admin", 1001L, LoginType.PLATFORM_ADMIN, true, false, null);
        register("platform-user", 1101L, LoginType.PLATFORM_USER, true, false, null);
        register("tenant-admin", 2101L, LoginType.TENANT_ADMIN, true, false, null);
        register("user", 2001L, LoginType.TENANT_USER, true, false, null);
        register("client", 3001L, LoginType.APP, true, false, null);
        register("service", 3101L, LoginType.SERVICE, true, false, null);
        register("frozen-user", 2002L, LoginType.TENANT_USER, false, false, null);
        register("must-change", 2003L, LoginType.TENANT_USER, true, true, null);
        register("expired-pwd", 2004L, LoginType.TENANT_USER, true, false, LocalDateTime.now().minusDays(100));
        register("fresh-pwd", 2005L, LoginType.TENANT_USER, true, false, LocalDateTime.now().minusDays(1));
    }

    public void register(String username, Long userId, LoginType type, boolean enabled,
                         boolean mustChangePassword, LocalDateTime passwordChangedAt) {
        MutableAccount account = new MutableAccount(userId, username, type,
                BCryptUtils.hash(DEFAULT_PASSWORD), enabled, mustChangePassword,
                passwordChangedAt == null ? LocalDateTime.now().minusDays(1) : passwordChangedAt);
        byUsername.put(key(username, type), account);
    }

    public MutableAccount mutable(String username, LoginType type) {
        return byUsername.get(key(username, type));
    }

    private String key(String username, LoginType type) {
        return type.value() + ":" + username;
    }

    @Override
    public PasswordAccount findAccount(String username, LoginType type) {
        MutableAccount account = byUsername.get(key(username, type));
        return account == null ? null : account.toPasswordAccount();
    }

    @Override
    public PasswordAccount findAccountByUserId(Long userId, LoginType type) {
        return byUsername.values().stream()
                .filter(item -> item.userId.equals(userId) && item.type == type)
                .findFirst().map(MutableAccount::toPasswordAccount).orElse(null);
    }

    @Override
    public void updatePassword(Long userId, String newPasswordBcrypt, LoginType type) {
        byUsername.values().stream()
                .filter(item -> item.userId.equals(userId) && item.type == type)
                .findFirst().ifPresent(item -> {
                    item.passwordHash = newPasswordBcrypt;
                    item.passwordChangedAt = LocalDateTime.now();
                    item.mustChangePassword = false;
                });
    }

    public static class MutableAccount {

        public final Long userId;

        public final String username;

        public final LoginType type;

        public String passwordHash;

        public boolean enabled;

        public boolean mustChangePassword;

        public LocalDateTime passwordChangedAt;

        public MutableAccount(Long userId, String username, LoginType type, String passwordHash, boolean enabled,
                              boolean mustChangePassword, LocalDateTime passwordChangedAt) {
            this.userId = userId;
            this.username = username;
            this.type = type;
            this.passwordHash = passwordHash;
            this.enabled = enabled;
            this.mustChangePassword = mustChangePassword;
            this.passwordChangedAt = passwordChangedAt;
        }

        public PasswordAccount toPasswordAccount() {
            return new PasswordAccount(userId, username, passwordHash, enabled, passwordChangedAt,
                    mustChangePassword);
        }
    }
}
