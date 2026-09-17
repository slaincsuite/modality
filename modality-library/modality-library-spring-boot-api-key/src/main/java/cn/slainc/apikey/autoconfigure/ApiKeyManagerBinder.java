package cn.slainc.apikey.autoconfigure;

import cn.dev33.satoken.apikey.SaApiKeyManager;
import cn.dev33.satoken.apikey.config.SaApiKeyConfig;
import cn.dev33.satoken.apikey.loader.SaApiKeyDataLoader;
import cn.dev33.satoken.apikey.template.SaApiKeyTemplate;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.slainc.apikey.config.ApiKeyProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

public class ApiKeyManagerBinder implements SmartInitializingSingleton {

    private final SaApiKeyTemplate template;

    private final ObjectProvider<SaApiKeyDataLoader> dataLoader;

    private final ApiKeyProperties properties;

    public ApiKeyManagerBinder(SaApiKeyTemplate template, ObjectProvider<SaApiKeyDataLoader> dataLoader,
                               ApiKeyProperties properties) {
        this.template = template;
        this.dataLoader = dataLoader;
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (template == null) {
            return;
        }
        SaApiKeyDataLoader loader = dataLoader.getIfAvailable();
        if (loader != null) {
            SaApiKeyManager.setSaApiKeyDataLoader(loader);
        }
        SaApiKeyConfig config = new SaApiKeyConfig();
        config.setPrefix(properties.getKeyPrefix());
        config.setTimeout(properties.getDefaultTtlDays() > 0
                ? properties.getDefaultTtlDays() * 86400 : SaTokenDao.NEVER_EXPIRE);
        SaApiKeyManager.setConfig(config);
        SaApiKeyManager.setSaApiKeyTemplate(template);
    }
}
