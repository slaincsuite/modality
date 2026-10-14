package cn.slainc.modules.auth.service.token;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.jwt.issue.SignedToken;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.issue.TokenType;
import cn.slainc.jwt.refresh.RefreshTokenService;
import cn.slainc.jwt.refresh.RefreshedTokens;
import cn.slainc.jwt.revoke.RevocationService;
import cn.slainc.jwt.introspect.IntrospectionResult;
import cn.slainc.jwt.introspect.IntrospectionService;
import cn.slainc.jwt.verify.JwtVerification;
import cn.slainc.jwt.verify.JwtVerifyException;
import cn.slainc.jwt.verify.JwtVerifier;

import java.util.List;
import java.util.Map;

public class AuthTokenService {

    private final JwtVerifier verifier;

    private final RefreshTokenService refreshService;

    private final RevocationService revocationService;

    private final IntrospectionService introspectionService;

    private final TokenClientAuthenticator clientAuthenticator;

    private final TokenEndpointGuard endpointGuard;

    public AuthTokenService(JwtVerifier verifier, RefreshTokenService refreshService,
                            RevocationService revocationService, IntrospectionService introspectionService) {
        this(verifier, refreshService, revocationService, introspectionService, null, null);
    }

    public AuthTokenService(JwtVerifier verifier, RefreshTokenService refreshService,
                            RevocationService revocationService, IntrospectionService introspectionService,
                            TokenClientAuthenticator clientAuthenticator, TokenEndpointGuard endpointGuard) {
        this.verifier = verifier;
        this.refreshService = refreshService;
        this.revocationService = revocationService;
        this.introspectionService = introspectionService;
        this.clientAuthenticator = clientAuthenticator;
        this.endpointGuard = endpointGuard;
    }

    public record TokenPairView(String accessToken, String refreshToken, String tokenType,
                                long accessExpiresAtMillis, long refreshExpiresAtMillis) {

        public static TokenPairView of(RefreshedTokens tokens) {
            return new TokenPairView(tokens.accessToken().tokenValue(), tokens.refreshToken().tokenValue(),
                    tokens.accessToken().tokenType().name(),
                    epochMilli(tokens.accessToken().expiresAt()), epochMilli(tokens.refreshToken().expiresAt()));
        }

        private static long epochMilli(java.time.Instant instant) {
            return instant == null ? 0 : instant.toEpochMilli();
        }
    }

    public TokenPairView refresh(String refreshToken) {
        JwtVerification verification = verifyStrict(refreshToken);
        if (verification.tokenType() != TokenType.REFRESH) {
            throw new BizException("提供的令牌不是刷新令牌，无法刷新");
        }
        Map<String, Object> claims = verification.claims();
        TokenIssuanceSpec.Builder builder = TokenIssuanceSpec.builder(String.valueOf(claims.get("sub")));
        if (claims.get("aud") instanceof List<?> audiences) {
            builder.audiences(audiences.stream().map(String::valueOf).toList());
        } else if (claims.get("aud") instanceof String audience) {
            builder.audience(audience);
        }
        try {
            return TokenPairView.of(refreshService.refresh(refreshToken, builder.build()));
        } catch (JwtVerifyException e) {
            throw new BizException("刷新令牌无效或已过期：" + e.getMessage());
        }
    }

    public boolean revoke(String token) {
        if (endpointGuard != null) {
            endpointGuard.checkRevoke();
        }
        try {
            revocationService.revokeByToken(token);
        } catch (JwtVerifyException e) {
            throw new BizException("令牌无效，无法吊销：" + e.getMessage());
        }
        return true;
    }

    public IntrospectionResult introspect(String token, String clientId, String clientSecret) {
        if (endpointGuard != null) {
            endpointGuard.checkIntrospect();
        }
        if (clientAuthenticator == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "introspect 需要客户端鉴权，但未装配客户端鉴权组件");
        }
        clientAuthenticator.requireAuthenticated(clientId, clientSecret);
        return introspectionService.introspect(token);
    }

    private JwtVerification verifyStrict(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException("令牌不能为空");
        }
        try {
            JwtVerification verification = verifier.verifyStrict(token);
            if (!verification.valid()) {
                throw new BizException("令牌校验未通过");
            }
            return verification;
        } catch (JwtVerifyException e) {
            throw new BizException("刷新令牌无效或已过期：" + e.getMessage());
        }
    }
}
