package cn.slainc.sso.template;

import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;

public class DynamicSsoClientTemplate extends SaSsoClientTemplate {

    private final SaSsoClientConfig dynamicConfig;

    public DynamicSsoClientTemplate(SaSsoClientConfig config) {
        this.dynamicConfig = config;
    }

    @Override
    public SaSsoClientConfig getClientConfig() {
        return dynamicConfig;
    }

    public void updateServerUrl(String serverUrl) {
        dynamicConfig.setServerUrl(serverUrl);
    }
}
