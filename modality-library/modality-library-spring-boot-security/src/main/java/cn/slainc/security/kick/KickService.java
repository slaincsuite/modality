package cn.slainc.security.kick;

import cn.dev33.satoken.stp.StpLogic;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.config.SecurityKeys;
import cn.slainc.security.audit.SecurityAuditRecorder;
import cn.slainc.security.metrics.SecurityMetricsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class KickService {

    private static final Logger log = LoggerFactory.getLogger(KickService.class);

    private final StringRedisTemplate redis;

    private final SecurityConfigReader config;

    private final SecurityAuditRecorder recorder;

    private final SecurityMetricsPort metrics;

    public KickService(StringRedisTemplate redis, SecurityConfigReader config, SecurityAuditRecorder recorder,
                       SecurityMetricsPort metrics) {
        this.redis = redis;
        this.config = config;
        this.recorder = recorder;
        this.metrics = metrics;
    }

    public void kickout(LoginType type, Object loginId, String reason) {
        kickout(type, loginId, null, null, reason);
    }

    public void kickout(LoginType type, Object loginId, String deviceType, String reason) {
        kickout(type, loginId, deviceType, null, reason);
    }

    public void kickout(LoginType type, Object loginId, String deviceType, String tokenValue, String reason) {
        StpLogic logic = cn.slainc.security.StpKit.of(type);
        String actualToken = tokenValue;
        if (actualToken == null) {
            actualToken = findOldestToken(logic, loginId, deviceType);
        }
        if (tokenValue != null) {
            logic.kickoutByTokenValue(tokenValue);
        } else if (deviceType == null) {
            logic.kickout(loginId);
        } else {
            logic.kickout(loginId, deviceType);
        }
        record(type, loginId, actualToken, deviceType, reason == null ? "管理员强制下线" : reason);
    }

    public void kickoutToken(LoginType type, String tokenValue, String reason) {
        StpLogic logic = cn.slainc.security.StpKit.of(type);
        // 必须先解析 loginId：踢下线后该 token 已无法反查账号，审计与事件会拿到 null
        Object loginId = logic.getLoginIdByToken(tokenValue);
        logic.kickoutByTokenValue(tokenValue);
        record(type, loginId, tokenValue, null, reason == null ? "管理员强制下线" : reason);
    }

    private void record(LoginType type, Object loginId, String tokenValue, String deviceType, String reason) {
        long ttl = 0;
        if (tokenValue != null) {
            try {
                ttl = cn.slainc.security.StpKit.of(type).getTokenTimeout(tokenValue);
            } catch (Exception e) {
                ttl = 0;
            }
        }
        if (ttl <= 0) {
            ttl = config.sessionTimeout(type);
        }
        if (tokenValue != null) {
            try {
                redis.opsForValue().set(SecurityKeys.kickReasonKey(tokenValue), reason, Duration.ofSeconds(ttl));
            } catch (Exception e) {
                log.warn("记录踢人原因失败：{}", e.getMessage());
            }
        }
        recorder.kickout(type, loginId, tokenValue, deviceType, reason);
        metrics.kickOut();
    }

    public String kickReason(String tokenValue) {
        try {
            return redis.opsForValue().get(SecurityKeys.kickReasonKey(tokenValue));
        } catch (Exception e) {
            return null;
        }
    }

    private String findOldestToken(StpLogic logic, Object loginId, String deviceType) {
        try {
            var terminals = logic.getTerminalListByLoginId(loginId, deviceType);
            if (terminals == null || terminals.isEmpty()) {
                return null;
            }
            return terminals.stream().min(java.util.Comparator.comparingLong(
                    cn.dev33.satoken.session.SaTerminalInfo::getCreateTime)).map(
                    cn.dev33.satoken.session.SaTerminalInfo::getTokenValue).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
