package cn.slainc.security.password;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.config.SecurityKeys;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.AccountPasswordUpdater;
import cn.slainc.security.spi.PasswordAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PasswordPolicyService {

    private static final Logger log = LoggerFactory.getLogger(PasswordPolicyService.class);

    private static final Set<String> WEAK_PASSWORDS = Set.of("123456", "1234567", "12345678", "123456789",
            "password", "password123", "qwerty", "abc123", "111111", "123123", "000000", "admin", "admin123",
            "letmein", "iloveyou");

    private final SecurityConfigReader config;

    private final ObjectProvider<AccountAuthProvider> authProvider;

    private final ObjectProvider<AccountPasswordUpdater> passwordUpdater;

    private final StringRedisTemplate redis;

    public PasswordPolicyService(SecurityConfigReader config, ObjectProvider<AccountAuthProvider> authProvider,
                                 ObjectProvider<AccountPasswordUpdater> passwordUpdater, StringRedisTemplate redis) {
        this.config = config;
        this.authProvider = authProvider;
        this.passwordUpdater = passwordUpdater;
        this.redis = redis;
    }

    public List<String> validateStrength(String raw) {
        List<String> violations = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            violations.add("密码不能为空");
            return violations;
        }
        int minLength = config.passwordMinLength();
        if (raw.length() < minLength) {
            violations.add("密码长度不能少于 " + minLength + " 位");
        }
        int classes = 0;
        if (raw.chars().anyMatch(Character::isLowerCase)) {
            classes++;
        }
        if (raw.chars().anyMatch(Character::isUpperCase)) {
            classes++;
        }
        if (raw.chars().anyMatch(Character::isDigit)) {
            classes++;
        }
        if (raw.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) {
            classes++;
        }
        int required = config.passwordRequireClasses();
        if (classes < required) {
            violations.add("密码需至少包含 " + required + " 类字符（小写字母、大写字母、数字、特殊字符）");
        }
        if (WEAK_PASSWORDS.contains(raw.toLowerCase())) {
            violations.add("密码过于简单，属于常见弱密码");
        }
        return violations;
    }

    public void validateStrengthAndThrow(String raw) {
        List<String> violations = validateStrength(raw);
        if (!violations.isEmpty()) {
            throw new BizException("密码强度不达标：" + String.join("；", violations));
        }
    }

    public boolean isExpired(PasswordAccount account) {
        int maxAgeDays = config.passwordMaxAgeDays();
        if (maxAgeDays <= 0) {
            return false;
        }
        if (account == null || account.passwordChangedAt() == null) {
            return false;
        }
        return account.passwordChangedAt().plusDays(maxAgeDays).isBefore(LocalDateTime.now());
    }

    public boolean isExpired(Long userId, LoginType type) {
        return isExpired(findByUserId(userId, type));
    }

    public boolean isUsedInHistory(Long userId, LoginType type, String raw) {
        String key = SecurityKeys.passwordHistoryKey(type.value(), userId);
        int size = Math.max(config.passwordHistorySize(), 0);
        if (size == 0) {
            return false;
        }
        try {
            List<String> history = redis.opsForList().range(key, 0, size - 1);
            if (history == null) {
                return false;
            }
            return history.stream().anyMatch(old -> BCryptUtils.verifyQuietly(raw, old));
        } catch (Exception e) {
            log.warn("读取密码历史失败，跳过历史比对：{}", e.getMessage());
            return false;
        }
    }

    public void recordHistory(Long userId, LoginType type, String newPasswordBcrypt) {
        String key = SecurityKeys.passwordHistoryKey(type.value(), userId);
        int size = Math.max(config.passwordHistorySize(), 0);
        if (size == 0) {
            return;
        }
        try {
            redis.opsForList().leftPush(key, newPasswordBcrypt);
            redis.opsForList().trim(key, 0, size - 1);
            redis.expire(key, Duration.ofDays(180));
        } catch (Exception e) {
            log.warn("记录密码历史失败：{}", e.getMessage());
        }
    }

    public void changePassword(LoginType type, Long userId, String oldRaw, String newRaw) {
        changePassword(type, userId, oldRaw, newRaw, false);
    }

    public void changePassword(LoginType type, Long userId, String oldRaw, String newRaw, boolean kickOtherSessions) {
        AccountPasswordUpdater updater = passwordUpdater.getIfAvailable();
        if (updater == null) {
            throw new BizException("未接入账号存储，无法修改密码");
        }
        PasswordAccount account = findByUserId(userId, type);
        if (account == null) {
            throw new BizException("账号不存在，无法修改密码");
        }
        if (oldRaw == null || !BCryptUtils.verifyQuietly(oldRaw, account.passwordHash())) {
            throw new BizException("旧密码不正确");
        }
        validateStrengthAndThrow(newRaw);
        if (isUsedInHistory(userId, type, newRaw)) {
            throw new BizException("新密码不能与最近 " + config.passwordHistorySize() + " 次使用过的密码相同");
        }
        String newHash = BCryptUtils.hash(newRaw);
        String previousHash = account.passwordHash();
        updater.updatePassword(userId, newHash, type);
        recordHistory(userId, type, previousHash);
        log.info("密码修改成功：账号体系={}，账号={}", type.value(), account.username());
        if (kickOtherSessions && account.userId() != null) {
            try {
                cn.slainc.security.StpKit.of(type).logout(account.userId());
            } catch (Exception e) {
                log.warn("修改密码后清理其它会话失败：{}", e.getMessage());
            }
        }
    }

    private PasswordAccount findByUserId(Long userId, LoginType type) {
        AccountAuthProvider provider = authProvider.getIfAvailable();
        if (provider == null) {
            return null;
        }
        return provider.findAccountByUserId(userId, type);
    }
}
