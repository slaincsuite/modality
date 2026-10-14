package cn.slainc.modules.auth.service.token;

import cn.dev33.satoken.oauth2.template.SaOAuth2Template;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import org.springframework.beans.factory.ObjectProvider;

// RFC 7662 要求 introspect 调用方必须完成客户端鉴权，禁止匿名探测令牌有效性
public class TokenClientAuthenticator {

    private final ObjectProvider<SaOAuth2Template> template;

    public TokenClientAuthenticator(ObjectProvider<SaOAuth2Template> template) {
        this.template = template;
    }

    public void requireAuthenticated(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw unauthorized("introspect 需要客户端鉴权：请提供 client_id 与 client_secret");
        }
        SaOAuth2Template oauth2 = template == null ? null : template.getIfAvailable();
        if (oauth2 == null) {
            throw unauthorized("introspect 需要客户端鉴权，但未装配 oauth2 客户端模板");
        }
        try {
            oauth2.checkClientSecret(clientId, clientSecret);
        } catch (RuntimeException exception) {
            throw unauthorized("客户端鉴权失败：client_id 或 client_secret 不正确");
        }
    }

    private BizException unauthorized(String message) {
        return new BizException(ResultCode.UNAUTHORIZED, message);
    }
}
