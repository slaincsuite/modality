package cn.slainc.session.timeout;

import cn.slainc.session.SessionInfo;
import cn.slainc.session.config.SessionConfigReader;

import java.time.Duration;
import java.time.LocalDateTime;

public class SessionTimeoutPolicy {

    public static final String IDLE_REASON = "会话空闲超时，已强制注销";

    public static final String LIFETIME_REASON = "会话超过最大生命周期，已强制注销";

    private final SessionConfigReader config;

    public SessionTimeoutPolicy(SessionConfigReader config) {
        this.config = config;
    }

    public long idleTimeoutSeconds() {
        return config.idleTimeoutSeconds();
    }

    public long maxLifetimeSeconds() {
        return config.maxLifetimeSeconds();
    }

    public boolean idleExceeded(SessionInfo session, long idleSeconds) {
        if (session == null || idleSeconds <= 0 || session.lastActiveTime() == null) {
            return false;
        }
        return idleMillis(session) > idleSeconds * 1000L;
    }

    public long idleMillis(SessionInfo session) {
        if (session == null || session.lastActiveTime() == null) {
            return 0L;
        }
        return Math.max(Duration.between(session.lastActiveTime(), LocalDateTime.now()).toMillis(), 0L);
    }

    public boolean absoluteExpired(SessionInfo session) {
        long maxLifetime = maxLifetimeSeconds();
        if (session == null || session.loginTime() == null || maxLifetime <= 0) {
            return false;
        }
        return Duration.between(session.loginTime(), LocalDateTime.now()).getSeconds() > maxLifetime;
    }

    public long remainingLifetimeSeconds(SessionInfo session) {
        long maxLifetime = maxLifetimeSeconds();
        if (session == null || session.loginTime() == null || maxLifetime <= 0) {
            return -1L;
        }
        return maxLifetime - Duration.between(session.loginTime(), LocalDateTime.now()).getSeconds();
    }
}
