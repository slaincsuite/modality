package cn.slainc.jwt.revoke;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.jwt.JwtCodec;
import cn.slainc.jwt.verify.JwtVerifyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class RevocationService {

    private static final Logger log = LoggerFactory.getLogger(RevocationService.class);

    private final RevocationStore store;

    public RevocationService(RevocationStore store) {
        this.store = store;
    }

    public RevocationStore store() {
        return store;
    }

    public void revokeByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtVerifyException("被吊销的令牌不能为空");
        }
        try {
            var jwt = JwtCodec.decodeUnverified(token);
            String jti = jwt.jwtId();
            if (jti == null || jti.isBlank()) {
                throw new JwtVerifyException("令牌缺少 jti 声明，无法按令牌吊销");
            }
            revoke(jti, jwt.expiresAt());
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
