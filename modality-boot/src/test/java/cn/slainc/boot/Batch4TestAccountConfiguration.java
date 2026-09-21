package cn.slainc.boot;

import cn.slainc.core.enums.LoginType;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class Batch4TestAccountConfiguration {

    public static final String PASSWORD = "Password#123";

    @Bean
    public AccountAuthProvider batch4AccountAuthProvider() {
        String hash = BCryptUtils.hash(PASSWORD);
        LocalDateTime changedAt = LocalDateTime.now().minusDays(1);
        Map<String, PasswordAccount> accounts = new LinkedHashMap<>();
        accounts.put(key(LoginType.PLATFORM_ADMIN, "admin"),
                new PasswordAccount(1001L, "admin", hash, true, changedAt, false));
        accounts.put(key(LoginType.TENANT_USER, "user"),
                new PasswordAccount(2001L, "user", hash, true, changedAt, false));
        accounts.put(key(LoginType.APP, "client"),
                new PasswordAccount(3001L, "client", hash, true, changedAt, false));
        return new AccountAuthProvider() {

            @Override
            public PasswordAccount findAccount(String username, LoginType type) {
                return accounts.get(key(type, username));
            }

            @Override
            public PasswordAccount findAccountByUserId(Long userId, LoginType type) {
                return accounts.values().stream()
                        .filter(account -> account.userId().equals(userId))
                        .findFirst().orElse(null);
            }
        };
    }

    private static String key(LoginType type, String username) {
        return type.value() + ":" + username;
    }
}
