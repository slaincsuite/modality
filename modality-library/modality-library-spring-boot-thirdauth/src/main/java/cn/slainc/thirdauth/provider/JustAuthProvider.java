package cn.slainc.thirdauth.provider;

import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.ThirdPartyToken;
import cn.slainc.thirdauth.ThirdPartyUser;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;

public abstract class JustAuthProvider implements ThirdPartyAuthProvider {

    protected final ThirdAuthConfigRegistry configRegistry;

    protected JustAuthProvider(ThirdAuthConfigRegistry configRegistry) {
        this.configRegistry = configRegistry;
    }

    protected abstract AuthRequest createRequest(AuthConfig config);

    protected void applyScene(AuthConfig.AuthConfigBuilder builder) {
    }

    @Override
    public String getAuthorizeUrl(String state, String redirectUri) {
        AuthConfig config = buildConfig(redirectUri);
        return createRequest(config).authorize(state);
    }

    @Override
    public ThirdPartyUser getUserInfo(String code, String state) {
        AuthRequest request = createRequest(buildConfig(null));
        AuthCallback callback = AuthCallback.builder().code(code).state(state).build();
        try {
            AuthToken token = request.getAccessToken(callback);
            AuthUser user = request.getUserInfo(token);
            return mapUser(user, token);
        } catch (AuthException exception) {
            throw new ThirdPartyException(cn.slainc.core.result.ResultCode.THIRD_PARTY_ERROR,
                getPlatform().label() + "登录失败: " + exception.getMessage(), exception);
        }
    }

    @Override
    public boolean supportsRefresh() {
        return false;
    }

    protected cn.slainc.thirdauth.ThirdPartyUser refreshByToken(String refreshToken) {
        AuthRequest request = createRequest(buildConfig(null));
        AuthToken input = new AuthToken();
        input.setRefreshToken(refreshToken);
        AuthResponse<AuthToken> response;
        try {
            response = request.refresh(input);
        } catch (AuthException exception) {
            throw new ThirdPartyException(cn.slainc.core.result.ResultCode.THIRD_PARTY_ERROR,
                    getPlatform().label() + "刷新访问令牌失败: " + exception.getMessage(), exception);
        }
        AuthToken refreshed = unwrap(response, getPlatform().label() + "刷新访问令牌失败");
        ThirdPartyUser user = new ThirdPartyUser();
        user.setPlatform(getPlatform());
        ThirdPartyToken token = new ThirdPartyToken();
        token.setAccessToken(refreshed.getAccessToken());
        token.setRefreshToken(refreshed.getRefreshToken());
        token.setExpireIn(refreshed.getExpireIn());
        user.setToken(token);
        return user;
    }

    protected ThirdPartyUser mapUser(AuthUser user, AuthToken token) {
        ThirdPartyUser mapped = new ThirdPartyUser();
        mapped.setPlatform(getPlatform());
        mapped.setOpenId(firstNonBlank(token.getOpenId(), user.getUuid()));
        mapped.setUnionId(token.getUnionId());
        mapped.setNickname(user.getNickname());
        mapped.setAvatar(user.getAvatar());
        ThirdPartyToken thirdToken = new ThirdPartyToken();
        thirdToken.setAccessToken(token.getAccessToken());
        thirdToken.setRefreshToken(token.getRefreshToken());
        thirdToken.setExpireIn(token.getExpireIn());
        mapped.setToken(thirdToken);
        return mapped;
    }

    protected AuthConfig buildConfig(String redirectUri) {
        cn.slainc.thirdauth.config.ThirdAuthProperties.Platform config = configRegistry.require(getPlatform());
        String effectiveRedirect = redirectUri != null && !redirectUri.isBlank()
                ? redirectUri : configRegistry.redirectUriOf(getPlatform());
        AuthConfig.AuthConfigBuilder builder = AuthConfig.builder()
                .clientId(config.getAppKey())
                .clientSecret(config.getAppSecret())
                .redirectUri(effectiveRedirect);
        if (config.getAgentId() != null && !config.getAgentId().isBlank()) {
            builder.agentId(config.getAgentId());
        }
        applyScene(builder);
        return builder.build();
    }

    protected <T> T unwrap(AuthResponse<T> response, String action) {
        if (response == null || !response.ok()) {
            String detail = response == null ? "无响应" : response.getMsg();
            throw new ThirdPartyException(action + ": " + detail);
        }
        return response.getData();
    }

    protected static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
