package cn.slainc.modules.auth.service.policy;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.policy.SecurityPolicy;
import cn.slainc.modules.auth.store.SecurityPolicyStore;
import cn.slainc.security.config.SecurityProperties;
import cn.slainc.security.config.SecurityKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class SecurityPolicyService {

    private static final Logger log = LoggerFactory.getLogger(SecurityPolicyService.class);

    private static final long MIN_SECONDS = Duration.ofMinutes(1).toSeconds();

    private static final long MAX_SECONDS = Duration.ofDays(30).toSeconds();

    private final SecurityPolicyStore store;

    private final SecurityProperties properties;

    private final ObjectProvider<DynamicConfigService> dynamicConfig;

    private final ObjectProvider<StringRedisTemplate> redis;

    public SecurityPolicyService(SecurityPolicyStore store, SecurityProperties properties,
                                 ObjectProvider<DynamicConfigService> dynamicConfig,
                                 ObjectProvider<StringRedisTemplate> redis) {
        this.store = store;
        this.properties = properties;
        this.dynamicConfig = dynamicConfig;
        this.redis = redis;
    }

    public record MfaPolicyView(boolean loginProtect, boolean operationProtect) {
    }

    public record LoginPolicyView(boolean remoteCheck) {
    }

    public record SessionPolicyView(long idleTimeoutSeconds, long maxSessionSeconds) {
    }

    public MfaPolicyView mfaPolicy() {
        SecurityPolicy policy = current();
        return new MfaPolicyView(Boolean.TRUE.equals(policy.getLoginProtect()),
                Boolean.TRUE.equals(policy.getOperationProtect()));
    }

    public MfaPolicyView updateMfaPolicy(Boolean loginProtect, Boolean operationProtect) {
        if (loginProtect == null || operationProtect == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "登录保护与操作保护开关不能为空");
        }
        SecurityPolicy policy = current();
        policy.setLoginProtect(loginProtect);
        policy.setOperationProtect(operationProtect);
        store.save(policy);
        return new MfaPolicyView(loginProtect, operationProtect);
    }

    public LoginPolicyView loginPolicy() {
        SecurityPolicy policy = current();
        return new LoginPolicyView(Boolean.TRUE.equals(policy.getRemoteCheck()));
    }

    public LoginPolicyView updateLoginPolicy(Boolean remoteCheck) {
        if (remoteCheck == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "异地登录校验开关不能为空");
        }
        SecurityPolicy policy = current();
        policy.setRemoteCheck(remoteCheck);
        store.save(policy);
        return new LoginPolicyView(remoteCheck);
    }

    public SessionPolicyView sessionPolicy() {
        SecurityPolicy policy = current();
        return new SessionPolicyView(policy.getIdleTimeoutSeconds(), policy.getMaxSessionSeconds());
    }

    public SessionPolicyView updateSessionPolicy(Long idleTimeoutSeconds, Long maxSessionSeconds) {
        validateSessionPolicy(idleTimeoutSeconds, maxSessionSeconds);
        SecurityPolicy policy = current();
        policy.setIdleTimeoutSeconds(idleTimeoutSeconds);
        policy.setMaxSessionSeconds(maxSessionSeconds);
        store.save(policy);
        applySessionPolicy(policy);
        return new SessionPolicyView(idleTimeoutSeconds, maxSessionSeconds);
    }

    public boolean loginProtectEnabled() {
        return Boolean.TRUE.equals(current().getLoginProtect());
    }

    public boolean remoteCheckEnabled() {
        return Boolean.TRUE.equals(current().getRemoteCheck());
    }

    public void alignTravelBaseline(LoginType type, Long userId, String clientIp) {
        if (type == null || userId == null || clientIp == null || clientIp.isBlank() || remoteCheckEnabled()) {
            return;
        }
        StringRedisTemplate template = redis == null ? null : redis.getIfAvailable();
        if (template == null) {
            return;
        }
        try {
            template.opsForValue().set(SecurityKeys.lastIpKey(type.value(), userId), clientIp, Duration.ofDays(7));
        } catch (RuntimeException e) {
            log.warn("更新异地登录基线失败：{}", e.getMessage());
        }
    }

    public SessionPolicyView effectiveSessionPolicy() {
        return sessionPolicy();
    }

    public void applySessionPolicy() {
        applySessionPolicy(current());
    }

    private SecurityPolicy current() {
        SecurityPolicy policy = store.find();
        if (policy == null) {
            policy = defaults();
            store.save(policy);
        }
        return policy;
    }

    private SecurityPolicy defaults() {
        SecurityProperties.Session session = properties.getSession();
        long idle = positiveOrDefault(session.getActiveTimeout(), 1800L);
        long max = positiveOrDefault(session.getTimeoutTenantUser(), 7200L);
        if (max < idle) {
            max = idle;
        }
        SecurityPolicy policy = new SecurityPolicy();
        policy.setLoginProtect(Boolean.TRUE);
        policy.setOperationProtect(Boolean.TRUE);
        policy.setRemoteCheck(Boolean.TRUE);
        policy.setIdleTimeoutSeconds(idle);
        policy.setMaxSessionSeconds(max);
        return policy;
    }

    private void applySessionPolicy(SecurityPolicy policy) {
        DynamicConfigService service = dynamicConfig == null ? null : dynamicConfig.getIfAvailable();
        if (service == null) {
            return;
        }
        try {
            service.set(AuthKeys.DYNAMIC_ACTIVE_TIMEOUT_KEY, String.valueOf(policy.getIdleTimeoutSeconds()), "auth");
            for (String key : AuthKeys.DYNAMIC_SESSION_TIMEOUT_KEYS) {
                service.set(key, String.valueOf(policy.getMaxSessionSeconds()), "auth");
            }
        } catch (RuntimeException e) {
            log.warn("同步会话策略到动态配置失败：{}", e.getMessage());
        }
    }

    private void validateSessionPolicy(Long idle, Long max) {
        if (idle == null || max == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "无操作超时与最大登录保持时间不能为空");
        }
        if (idle < MIN_SECONDS || idle > MAX_SECONDS) {
            throw new BizException(ResultCode.BAD_REQUEST, "无操作超时必须在 1 分钟到 30 天之间");
        }
        if (max < idle) {
            throw new BizException(ResultCode.BAD_REQUEST, "最大登录保持时间不能小于无操作超时");
        }
        if (max > MAX_SECONDS) {
            throw new BizException(ResultCode.BAD_REQUEST, "最大登录保持时间不能超过 30 天");
        }
    }

    private static long positiveOrDefault(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
