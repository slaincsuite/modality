package cn.slainc.thirdauth.provider;

import cn.slainc.core.exception.ThirdPartyException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.thirdauth.ThirdPartyPlatform;
import cn.slainc.thirdauth.ThirdPartyToken;
import cn.slainc.thirdauth.ThirdPartyUser;
import cn.slainc.thirdauth.config.ThirdAuthConfigRegistry;
import cn.slainc.utils.thread.ThreadPoolUtils;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.exception.AuthException;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public abstract class JustAuthProvider implements ThirdPartyAuthProvider {

    private static final ExecutorService CALL_EXECUTOR =
            ThreadPoolUtils.newVirtualThreadPerTaskExecutor("thirdauth-http");

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
        return callWithTimeout("获取用户信息", () -> {
            try {
                AuthToken token = request.getAccessToken(callback);
                AuthUser user = request.getUserInfo(token);
                return mapUser(user, token);
            } catch (AuthException exception) {
                throw new ThirdPartyException(ResultCode.THIRD_PARTY_ERROR,
                        getPlatform().label() + "登录失败: " + exception.getMessage(), exception);
            }
        });
    }

    @Override
    public boolean supportsRefresh() {
        return false;
    }

    protected cn.slainc.thirdauth.ThirdPartyUser refreshByToken(String refreshToken) {
        AuthRequest request = createRequest(buildConfig(null));
        AuthToken input = new AuthToken();
        input.setRefreshToken(refreshToken);
        AuthResponse<AuthToken> response = callWithTimeout("刷新访问令牌", () -> {
            try {
                return request.refresh(input);
            } catch (AuthException exception) {
                throw new ThirdPartyException(ResultCode.THIRD_PARTY_ERROR,
                        getPlatform().label() + "刷新访问令牌失败: " + exception.getMessage(), exception);
            }
        });
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

    // IdP 挂起会拖垮登录线程：所有外呼统一加可配置超时，超时后放弃等待并抛中文业务异常
    protected <T> T callWithTimeout(String action, Supplier<T> call) {
        long timeoutSeconds = configRegistry.requestTimeoutSeconds();
        try {
            return CompletableFuture.supplyAsync(call, CALL_EXECUTOR)
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof ThirdPartyException thirdPartyException) {
                throw thirdPartyException;
            }
            if (cause instanceof TimeoutException) {
                throw timeout(action, timeoutSeconds, cause);
            }
            throw new ThirdPartyException(ResultCode.THIRD_PARTY_ERROR,
                    getPlatform().label() + action + "失败：" + cause.getMessage(), cause);
        } catch (CancellationException exception) {
            throw timeout(action, timeoutSeconds, exception);
        }
    }

    private ThirdPartyException timeout(String action, long timeoutSeconds, Throwable cause) {
        return new ThirdPartyException(ResultCode.THIRD_PARTY_ERROR,
                getPlatform().label() + action + "超时，已放弃等待（超时上限 " + timeoutSeconds + " 秒）", cause);
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
