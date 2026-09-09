package cn.slainc.security.autoconfigure;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.slainc.security.config.SecurityProperties;

public class SecurityCookieCustomizer {

    public SecurityCookieCustomizer(SaTokenConfig saTokenConfig, SecurityProperties properties) {
        saTokenConfig.getCookie().setSameSite(properties.getCookie().getSameSite());
        saTokenConfig.getCookie().setHttpOnly(true);
    }
}
