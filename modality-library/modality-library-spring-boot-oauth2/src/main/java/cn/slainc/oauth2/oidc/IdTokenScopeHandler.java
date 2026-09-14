package cn.slainc.oauth2.oidc;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.oauth2.SaOAuth2Manager;
import cn.dev33.satoken.oauth2.consts.SaOAuth2Consts;
import cn.dev33.satoken.oauth2.data.model.AccessTokenModel;
import cn.dev33.satoken.oauth2.data.model.ClientTokenModel;
import cn.dev33.satoken.oauth2.scope.handler.SaOAuth2ScopeHandlerInterface;
import cn.dev33.satoken.session.SaSession;
import cn.slainc.jwt.issue.JwtTokenService;
import cn.slainc.jwt.issue.SignedToken;
import cn.slainc.jwt.issue.TokenIssuanceSpec;
import cn.slainc.jwt.issue.TokenType;
import cn.slainc.oauth2.config.Oauth2Properties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class IdTokenScopeHandler implements SaOAuth2ScopeHandlerInterface {

    public static final String SCOPE_OIDC = "oidc";

    public static final String SCOPE_OPENID = "openid";

    public static final String EXTRA_ID_TOKEN = "id_token";

    private final JwtTokenService tokenService;

    private final Oauth2Properties properties;

    private final String handlerScope;

    public IdTokenScopeHandler(JwtTokenService tokenService, Oauth2Properties properties, String handlerScope) {
        this.tokenService = tokenService;
        this.properties = properties;
        this.handlerScope = handlerScope;
    }

    @Override
    public String getHandlerScope() {
        return handlerScope;
    }

    @Override
    public void workAccessToken(AccessTokenModel at) {
        String clientId = at.clientId;
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("openid", SaOAuth2Manager.getDataLoader().getOpenid(clientId, at.loginId));
        TokenIssuanceSpec spec = TokenIssuanceSpec.builder(String.valueOf(at.loginId))
                .tokenType(TokenType.ID_TOKEN)
                .audience(clientId)
                .nonce(resolveNonce())
                .authTime(resolveAuthTime(at.loginId))
                .azp(clientId)
                .ttl(Duration.ofSeconds(properties.getOidc().getIdTokenSeconds()))
                .claims(claims)
                .build();
        SignedToken signed = tokenService.issueToken(spec);
        at.extraData.put(EXTRA_ID_TOKEN, signed.tokenValue());
    }

    @Override
    public void workClientToken(ClientTokenModel ct) {
    }

    @Override
    public boolean refreshAccessTokenIsWork() {
        return true;
    }

    private String resolveNonce() {
        String nonce = SaHolder.getRequest().getParam(SaOAuth2Consts.Param.nonce);
        if (nonce == null || nonce.isBlank()) {
            String code = SaHolder.getRequest().getParam(SaOAuth2Consts.Param.code);
            if (code != null && !code.isBlank()) {
                nonce = SaOAuth2Manager.getDao().getNonce(code);
            }
        }
        return nonce == null || nonce.isBlank() ? null : nonce;
    }

    private Long resolveAuthTime(Object loginId) {
        SaSession session = SaOAuth2Manager.getStpLogic().getSessionByLoginId(loginId, false);
        if (session == null) {
            return System.currentTimeMillis() / 1000;
        }
        return session.getCreateTime() / 1000;
    }
}
