package cn.slainc.jwt.refresh;

import cn.slainc.jwt.config.JwtSettings;
import cn.slainc.jwt.issue.SignedToken;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.issue.TokenType;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.revoke.RevocationService;
import cn.slainc.jwt.verify.JwtVerification;
import cn.slainc.jwt.verify.JwtVerifier;
import cn.slainc.jwt.verify.JwtVerifyException;

public class RefreshTokenService {

    private final JwtVerifier verifier;

    private final JwtTokenService tokenService;

    private final RevocationService revocationService;

    private final JwtSettings settings;

    public RefreshTokenService(JwtVerifier verifier, JwtTokenService tokenService, RevocationService revocationService,
                               JwtSettings settings) {
        this.verifier = verifier;
        this.tokenService = tokenService;
        this.revocationService = revocationService;
        this.settings = settings;
    }

    public RefreshedTokens refresh(String refreshToken, TokenIssuanceSpec newAccessSpec) {
        JwtVerification verification;
        try {
            verification = verifier.verifyStrict(refreshToken);
        } catch (JwtVerifyException exception) {
            throw new JwtVerifyException("刷新失败，刷新令牌无效：" + exception.getMessage(), exception);
        }
        if (!verification.valid()) {
            throw new JwtVerifyException("刷新失败，刷新令牌无效：" + verification.error());
        }
        if (verification.tokenType() != TokenType.REFRESH) {
            throw new JwtVerifyException("仅刷新令牌（REFRESH）可用于换发，实际令牌类型为 "
                    + (verification.tokenType() == null ? "未知" : verification.tokenType().name()));
        }
        if (settings.refreshRotation()) {
            revocationService.revokeByToken(refreshToken);
        }
        String subject = newAccessSpec != null && newAccessSpec.subject() != null ? newAccessSpec.subject()
                : String.valueOf(verification.claims().get("sub"));
        TokenIssuanceSpec.Builder accessBuilder = newAccessSpec == null
                ? TokenIssuanceSpec.builder(subject) : rebuild(newAccessSpec, subject);
        SignedToken access = tokenService.issueToken(accessBuilder.tokenType(TokenType.ACCESS).build());
        TokenIssuanceSpec.Builder refreshBuilder = TokenIssuanceSpec.builder(subject)
                .tokenType(TokenType.REFRESH);
        if (newAccessSpec != null) {
            refreshBuilder.audiences(newAccessSpec.audiences()).claims(newAccessSpec.claims());
        }
        SignedToken nextRefresh = tokenService.issueToken(refreshBuilder.build());
        return new RefreshedTokens(access, nextRefresh);
    }

    private TokenIssuanceSpec.Builder rebuild(TokenIssuanceSpec spec, String subject) {
        return TokenIssuanceSpec.builder(subject)
                .audiences(spec.audiences())
                .claims(spec.claims())
                .ttl(spec.ttl());
    }
}
