package cn.slainc.jwt.verify;

import cn.slainc.crypto.CryptoException;
import cn.slainc.crypto.jwt.Jwt;
import cn.slainc.crypto.jwt.JwtCodec;
import cn.slainc.crypto.jwt.JwtAlgorithm;
import cn.slainc.jwt.algorithm.JwtAlgorithmWhitelist;
import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.issue.TokenType;
import cn.slainc.jwt.key.JwtKeyEntry;
import cn.slainc.jwt.key.JwtKeyManager;
import cn.slainc.jwt.revoke.RevocationService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class JwtVerifier {

    private final JwtKeyManager keyManager;

    private final JwtAlgorithmWhitelist whitelist;

    private final JwtSettings settings;

    private final RevocationService revocationService;

    private final Clock clock;

    public JwtVerifier(JwtKeyManager keyManager, JwtAlgorithmWhitelist whitelist, JwtSettings settings,
                       RevocationService revocationService) {
        this(keyManager, whitelist, settings, revocationService, Clock.systemUTC());
    }

    public JwtVerifier(JwtKeyManager keyManager, JwtAlgorithmWhitelist whitelist, JwtSettings settings,
                       RevocationService revocationService, Clock clock) {
        this.keyManager = keyManager;
        this.whitelist = whitelist;
        this.settings = settings;
        this.revocationService = revocationService;
        this.clock = clock;
    }

    public JwtVerification verify(String token) {
        try {
            return verifyStrict(token);
        } catch (JwtVerifyException exception) {
            return JwtVerification.fail(exception.getMessage());
        }
    }

    public JwtVerification verifyStrict(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtVerifyException("令牌不能为空，已拒绝验签");
        }
        int maxLength = settings.maxTokenLength();
        if (token.length() > maxLength) {
            throw new JwtVerifyException("令牌长度 " + token.length() + " 超过上限 " + maxLength + "，已拒绝");
        }
        Jwt jwt = decodeSafely(token);
        String headerAlg = jwt.algorithm();
        if (headerAlg == null) {
            throw new JwtVerifyException("令牌头部缺少 alg 字段，已拒绝验签");
        }
        if ("none".equalsIgnoreCase(headerAlg)) {
            throw new JwtVerifyException("签名算法为 none 的令牌一律拒绝");
        }
        JwtAlgorithm algorithm;
        try {
            algorithm = JwtAlgorithm.fromAlg(headerAlg);
        } catch (CryptoException exception) {
            throw new JwtVerifyException(exception.getMessage(), exception);
        }
        whitelist.ensureAllowed(algorithm);
        String kid = jwt.keyId();
        if (kid == null || kid.isBlank()) {
            throw new JwtVerifyException("令牌头部缺少 kid，无法定位验签密钥");
        }
        JwtKeyEntry entry = keyManager.requireByKid(kid);
        if (entry.algorithm() != algorithm) {
            throw new JwtVerifyException("令牌头部 alg（" + algorithm.alg() + "）与密钥 kid=" + kid
                    + " 的实际算法（" + entry.algorithm().alg() + "）不一致，疑似算法混淆攻击，已拒绝");
        }
        try {
            JwtCodec.decode(token, entry.verifyKey(), algorithm);
        } catch (CryptoException exception) {
            throw new JwtVerifyException("令牌签名校验失败：" + exception.getMessage(), exception);
        }
        Map<String, Object> claims = jwt.claims();
        checkClaims(jwt, claims);
        String jti = jwt.jwtId();
        if (jti != null && revocationService.isRevoked(jti)) {
            throw new JwtVerifyException("令牌已被吊销（jti=" + jti + "）");
        }
        TokenType tokenType = TokenType.of(jwt.stringClaim("token_type"));
        return JwtVerification.ok(claims, kid, tokenType, jti);
    }

    private Jwt decodeSafely(String token) {
        try {
            return JwtCodec.decodeUnverified(token);
        } catch (CryptoException exception) {
            throw new JwtVerifyException("令牌格式非法，无法解析：" + exception.getMessage(), exception);
        }
    }

    private void checkClaims(Jwt jwt, Map<String, Object> claims) {
        Instant now = clock.instant();
        int skew = settings.clockSkewSeconds();
        Instant expiresAt = jwt.expiresAt();
        if (expiresAt != null && now.minusSeconds(skew).isAfter(expiresAt)) {
            throw new JwtVerifyException("令牌已过期（exp=" + expiresAt + "，当前时间=" + now + "）");
        }
        Instant notBefore = jwt.notBefore();
        if (notBefore != null && now.plusSeconds(skew).isBefore(notBefore)) {
            throw new JwtVerifyException("令牌尚未生效（nbf=" + notBefore + "，当前时间=" + now + "）");
        }
        String configuredIssuer = settings.issuer();
        if (configuredIssuer != null && !configuredIssuer.isBlank()) {
            String issuer = jwt.issuer();
            if (issuer == null) {
                throw new JwtVerifyException("令牌缺少 iss 声明，已拒绝");
            }
            if (!configuredIssuer.equals(issuer)) {
                throw new JwtVerifyException("令牌签发方不符，期望 iss=" + configuredIssuer + "，实际 iss=" + issuer);
            }
        }
        List<String> configuredAudiences = settings.audiences();
        if (configuredAudiences != null && !configuredAudiences.isEmpty()) {
            Object audClaim = claims.get("aud");
            List<String> tokenAudiences = normalizeAudience(audClaim);
            if (tokenAudiences.isEmpty()) {
                throw new JwtVerifyException("令牌缺少 aud 声明，已拒绝");
            }
            boolean matched = configuredAudiences.stream().anyMatch(tokenAudiences::contains);
            if (!matched) {
                throw new JwtVerifyException("令牌受众不符，期望 aud 包含 " + configuredAudiences + " 之一，实际 aud=" + tokenAudiences);
            }
        }
    }

    private List<String> normalizeAudience(Object audClaim) {
        if (audClaim instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (audClaim instanceof String value) {
            return List.of(value);
        }
        return List.of();
    }
}
