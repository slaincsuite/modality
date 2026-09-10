package cn.slainc.jwt.revoke;

import java.time.Instant;

public interface RevocationStore {

    void revoke(String jti, Instant expiresAt);

    boolean isRevoked(String jti);
}
