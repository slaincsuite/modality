package cn.slainc.jwt.revoke;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.jwt.JwtCodec;
import cn.slainc.jwt.verify.JwtVerifyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

public class RevocationService {

    // 令牌自报的 exp 不可信，按令牌吊销时 TTL 一律钳制到该上限，防止被构造出超长吊销记录撑爆存储
    public static final Duration MAX_REVOCATION_TTL = Duration.ofHours(24);

    private static final Logger log = LoggerFactory.getLogger(RevocationService.class);

    private final RevocationStore store;

    private final RevocationVerifier verifier;

    public RevocationService(RevocationStore store) {
        this(store, null);
    }

    public RevocationService(RevocationStore store, RevocationVerifier verifier) {
        this.store = store;
        this.verifier = verifier;
    }

    public RevocationStore store() {
        return store;
    }

    public void revokeByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtVerifyException("被吊销的令牌不能为空");
        }
        if (verifier == null) {
            throw new JwtVerifyException("未装配令牌验签器，拒绝按令牌吊销，请改用 revoke(jti, expiresAt)");
        }
        verifier.requireValid(token);
        try {
            var jwt = JwtCodec.decodeUnverified(token);
            String jti = jwt.jwtId();
            if (jti == null || jti.isBlank()) {
                throw new JwtVerifyException("令牌缺少 jti 声明，无法按令牌吊销");
            }
            revoke(jti, clampTtl(jwt.expiresAt()));
        } catch (CryptoException exception) {
            throw new JwtVerifyException("令牌格式非法，无法解析出吊销信息：" + exception.getMessage(), exception);
        }
    }

    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            throw new JwtVerifyException("吊销记录必须携带 jti");
        }
        store.revoke(jti, expiresAt);
        log.info("jwt 令牌已吊销，jti：{}，过期时间：{}", jti, expiresAt);
    }

    private Instant clampTtl(Instant expiresAt) {
        Instant upperBound = Instant.now().plus(MAX_REVOCATION_TTL);
        return expiresAt == null || expiresAt.isAfter(upperBound) ? upperBound : expiresAt;
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return store.isRevoked(jti);
        } catch (RuntimeException exception) {
            log.warn("查询令牌吊销状态失败，按未吊销放行（jti={}）", jti, exception);
            return false;
        }
    }
}
