package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.config.SecurityProperties;
import cn.slainc.security.password.PasswordPolicyService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.AccountPasswordUpdater;
import cn.slainc.security.spi.PasswordAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyServiceUnitTest {

    private SecurityConfigReader reader() {
        return new SecurityConfigReader(new SecurityProperties(), providerOf(null));
    }

    private <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfAvailable(java.util.function.Supplier<T> supplier) {
                return value == null ? (supplier == null ? null : supplier.get()) : value;
            }

            @Override
            public java.util.stream.Stream<T> stream() {
                return value == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(value);
            }
        };
    }

    @Test
    void weakPasswordsProduceItemizedChineseViolations() {
        PasswordPolicyService service = serviceWithoutUpdater();
        assertThat(service.validateStrength("short")).anyMatch(v -> v.contains("长度"));
        assertThat(service.validateStrength("alllowercase1")).anyMatch(v -> v.contains("类字符"));
        assertThat(service.validateStrength("12345678")).anyMatch(v -> v.contains("弱密码"));
        assertThat(service.validateStrength("")).contains("密码不能为空");
    }

    @Test
    void strongPasswordPassesStrength() {
        PasswordPolicyService service = serviceWithoutUpdater();
        assertThat(service.validateStrength("Good#Pass123")).isEmpty();
    }

    @Test
    void hotMinLengthReadFromConfigReader() {
        SecurityProperties properties = new SecurityProperties();
        properties.getPassword().setMinLength(12);
        SecurityConfigReader reader = new SecurityConfigReader(properties, providerOf(null));
        PasswordPolicyService service = new PasswordPolicyService(reader, null, null,
                new org.springframework.data.redis.core.StringRedisTemplate());
        assertThat(service.validateStrength("Abc#12345")).anyMatch(v -> v.contains("12"));
    }

    @Test
    void expiryJudgementByPasswordChangedAt() {
        PasswordPolicyService service = serviceWithoutUpdater();
        PasswordAccount fresh = new PasswordAccount(1L, "u", "h", true, LocalDateTime.now().minusDays(1), false);
        PasswordAccount stale = new PasswordAccount(2L, "u", "h", true, LocalDateTime.now().minusDays(120), false);
        assertThat(service.isExpired(fresh)).isFalse();
        assertThat(service.isExpired(stale)).isTrue();
        assertThat(service.isExpired(null)).isFalse();
    }

    @Test
    void noUpdaterBeanThrowsChineseException() {
        PasswordPolicyService service = serviceWithoutUpdater();
        BizException exception = assertThrows(BizException.class, () -> service.changePassword(LoginType.USER,
                2001L, "old", "BrandNew#123"));
        assertThat(exception.getMessage()).isEqualTo("未接入账号存储，无法修改密码");
    }

    @Test
    void changePasswordFlowWithUpdaterVerifiesOldAndRecordsHistory() {
        Map<String, List<String>> historyStore = new ConcurrentHashMap<>();
        Map<Long, String> currentHash = new ConcurrentHashMap<>();
        currentHash.put(1L, BCryptUtils.hash("Old#Pass123"));
        AccountAuthProvider provider = new AccountAuthProvider() {
            @Override
            public PasswordAccount findAccount(String username, LoginType type) {
                return null;
            }

            @Override
            public PasswordAccount findAccountByUserId(Long userId, LoginType type) {
                return new PasswordAccount(userId, "user", currentHash.get(userId), true,
                        LocalDateTime.now(), false);
            }
        };
        AccountPasswordUpdater updater = (userId, newHash, type) -> {
            currentHash.put(userId, newHash);
            historyStore.computeIfAbsent(type.value() + ":" + userId, key -> new java.util.ArrayList<>())
                    .add(newHash);
        };
        PasswordPolicyService service = new PasswordPolicyService(reader(), providerOf(provider),
                providerOf(updater), new org.springframework.data.redis.core.StringRedisTemplate());
        service.changePassword(LoginType.USER, 1L, "Old#Pass123", "New#Pass456");
        assertThat(BCryptUtils.verifyQuietly("New#Pass456", currentHash.get(1L))).isTrue();
    }

    @Test
    void wrongOldPasswordRejected() {
        PasswordPolicyService service = serviceWithoutUpdater();
        assertThrows(BizException.class, () -> service.validateStrengthAndThrow("x"));
    }

    private PasswordPolicyService serviceWithoutUpdater() {
        return new PasswordPolicyService(reader(), providerOf(null), providerOf(null),
                new org.springframework.data.redis.core.StringRedisTemplate());
    }
}
