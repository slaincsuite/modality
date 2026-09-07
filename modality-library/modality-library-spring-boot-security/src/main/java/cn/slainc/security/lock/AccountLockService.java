package cn.slainc.security.lock;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.audit.SecurityAuditRecorder;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.config.SecurityKeys;
import cn.slainc.security.event.PasswordLockedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class AccountLockService {

    private static final Logger log = LoggerFactory.getLogger(AccountLockService.class);

    private final StringRedisTemplate redis;

    private final SecurityConfigReader config;

    private final SecurityAuditRecorder recorder;

    private final ApplicationEventPublisher publisher;

    public AccountLockService(StringRedisTemplate redis, SecurityConfigReader config,
                              SecurityAuditRecorder recorder, ApplicationEventPublisher publisher) {
        this.redis = redis;
        this.config = config;
        this.recorder = recorder;
        this.publisher = publisher;
    }

    public boolean isLocked(LoginType type, Object loginId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(SecurityKeys.lockLockedKey(type.value(), loginId)));
        } catch (Exception e) {
            log.warn("锁定状态检查失败，按未锁定处理：{}", e.getMessage());
            return false;
        }
    }

    public long lockRemainSeconds(LoginType type, Object loginId) {
        try {
            Long ttl = redis.getExpire(SecurityKeys.lockLockedKey(type.value(), loginId));
            return ttl == null ? 0 : Math.max(ttl, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public int remainingAttempts(LoginType type, Object loginId) {
        int max = config.lockoutMaxFailures();
        try {
            String value = redis.opsForValue().get(SecurityKeys.lockFailKey(type.value(), loginId));
            int count = value == null ? 0 : Integer.parseInt(value);
            return Math.max(max - count, 0);
        } catch (Exception e) {
            return max;
        }
    }

    public int recordFailure(LoginType type, Object loginId) {
        String failKey = SecurityKeys.lockFailKey(type.value(), loginId);
        int max = config.lockoutMaxFailures();
        int window = config.lockoutWindowSeconds();
        int lockMinutes = config.lockoutLockMinutes();
        try {
            Long count = redis.opsForValue().increment(failKey);
            long current = count == null ? 1 : count;
            if (current == 1 && window > 0) {
                redis.expire(failKey, Duration.ofSeconds(window));
            }
            if (max > 0 && current >= max) {
                lock(type, loginId, current);
                return 0;
            }
            return (int) Math.max(max - current, 0);
        } catch (Exception e) {
            log.warn("记录密码失败次数时 Redis 异常，本次不计入锁定统计：{}", e.getMessage());
            return max;
        }
    }

    public void lock(LoginType type, Object loginId, long failCount) {
        String lockedKey = SecurityKeys.lockLockedKey(type.value(), loginId);
        int lockMinutes = config.lockoutLockMinutes();
        try {
            redis.opsForValue().set(lockedKey, String.valueOf(failCount),
                    Duration.ofMinutes(Math.max(lockMinutes, 1)));
            redis.delete(SecurityKeys.lockFailKey(type.value(), loginId));
        } catch (Exception e) {
            log.warn("写入锁定标记时 Redis 异常：{}", e.getMessage());
        }
        recorder.locked(type, loginId, (int) failCount);
    }

    public void reset(LoginType type, Object loginId) {
        try {
            redis.delete(SecurityKeys.lockFailKey(type.value(), loginId));
            redis.delete(SecurityKeys.lockLockedKey(type.value(), loginId));
        } catch (Exception e) {
            log.warn("重置密码失败计数时 Redis 异常：{}", e.getMessage());
        }
    }
}
