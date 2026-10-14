package cn.slainc.mfa.challenge;

import java.io.Serializable;
import java.time.Duration;

public record MfaChallenge(String id, Long userId, String username, String method,
                           long expiresAt) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    public static MfaChallenge of(String id, Long userId, String username, String method, Duration ttl) {
        return new MfaChallenge(id, userId, username, method, System.currentTimeMillis() + ttlMillis(ttl));
    }

    public boolean expired() {
        return expiredAt(System.currentTimeMillis());
    }

    public boolean expiredAt(long epochMillis) {
        return expiresAt <= epochMillis;
    }

    private static long ttlMillis(Duration ttl) {
        Duration effective = ttl == null || ttl.isZero() || ttl.isNegative() ? DEFAULT_TTL : ttl;
        return effective.toMillis();
    }
}
