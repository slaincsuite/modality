package cn.slainc.oauth2.userinfo;

import cn.dev33.satoken.oauth2.SaOAuth2Manager;
import cn.dev33.satoken.oauth2.data.model.AccessTokenModel;
import cn.dev33.satoken.oauth2.template.SaOAuth2Template;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.LoginTypes;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

public class Oauth2UserInfoService {

    private final ObjectProvider<SaOAuth2Template> template;

    private final ObjectProvider<AccountAuthProvider> accountProvider;

    private final String loginType;

    public Oauth2UserInfoService(ObjectProvider<SaOAuth2Template> template,
                                 ObjectProvider<AccountAuthProvider> accountProvider, String loginType) {
        this.template = template;
        this.accountProvider = accountProvider;
        this.loginType = loginType;
    }

    public Map<String, Object> userInfo(String accessToken) {
        SaOAuth2Template oauth2Template = template.getIfAvailable();
        if (oauth2Template == null) {
            throw new IllegalStateException("OAuth2 服务未初始化");
        }
        AccessTokenModel at = oauth2Template.checkAccessToken(accessToken);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sub", String.valueOf(at.loginId));
        result.put("client_id", at.clientId);
        result.put("scope", at.scopes == null ? "" : String.join(" ", at.scopes));
        result.put("openid", SaOAuth2Manager.getDataLoader().getOpenid(at.clientId, at.loginId));
        PasswordAccount account = findAccount(at.loginId);
        if (account != null) {
            result.put("username", account.username());
            result.put("preferred_username", account.username());
        }
        return result;
    }

    private PasswordAccount findAccount(Object loginId) {
        AccountAuthProvider provider = accountProvider.getIfAvailable();
        if (provider == null || loginId == null) {
            return null;
        }
        try {
            LoginType type = LoginTypes.resolveOrDefault(loginType, LoginType.TENANT_USER.value());
            return provider.findAccountByUserId(Long.parseLong(String.valueOf(loginId)), type);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
