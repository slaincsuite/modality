package cn.slainc.session.extend;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;
import cn.slainc.security.stp.SecurityStpLogic;
import cn.slainc.session.config.SessionConfigReader;

public class SessionRenewalService {

    private final SessionConfigReader config;

    public SessionRenewalService(SessionConfigReader config) {
        this.config = config;
    }

    public long renew(LoginType type, String tokenValue, long seconds) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new IllegalArgumentException("token 不能为空");
        }
        if (seconds <= 0) {
            throw new IllegalArgumentException("续期时长必须大于 0 秒");
        }
        SecurityStpLogic logic = StpKit.of(type);
        logic.renewTimeout(tokenValue, seconds);
        return logic.getTokenTimeout(tokenValue);
    }

    public long applyConfiguredTtl(LoginType type, String tokenValue) {
        return renew(type, tokenValue, config.effectiveTtlSeconds(type));
    }

    public long effectiveTtlSeconds(LoginType type) {
        return config.effectiveTtlSeconds(type);
    }

    public boolean renewIfNeeded(LoginType type, String tokenValue, long thresholdSeconds, long seconds) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }
        long remaining = StpKit.of(type).getTokenTimeout(tokenValue);
        if (remaining < 0) {
            return false;
        }
        if (thresholdSeconds > 0 && remaining > thresholdSeconds) {
            return false;
        }
        renew(type, tokenValue, seconds);
        return true;
    }
}
