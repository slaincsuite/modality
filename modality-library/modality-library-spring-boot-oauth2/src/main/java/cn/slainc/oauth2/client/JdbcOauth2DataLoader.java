package cn.slainc.oauth2.client;

import cn.dev33.satoken.oauth2.data.loader.SaOAuth2DataLoader;
import cn.dev33.satoken.oauth2.data.model.loader.SaClientModel;
import cn.slainc.oauth2.config.Oauth2Properties;
import cn.slainc.oauth2.scope.ScopeRegistry;

import java.util.List;
import java.util.Optional;

public class JdbcOauth2DataLoader implements SaOAuth2DataLoader {

    private final ClientRecordCache cache;

    private final ClientSecretCipher cipher;

    private final ScopeRegistry scopeRegistry;

    private final Oauth2Properties properties;

    public JdbcOauth2DataLoader(ClientRecordCache cache, ClientSecretCipher cipher,
                                ScopeRegistry scopeRegistry, Oauth2Properties properties) {
        this.cache = cache;
        this.cipher = cipher;
        this.scopeRegistry = scopeRegistry;
        this.properties = properties;
    }

    @Override
    public SaClientModel getClientModel(String clientId) {
        Optional<Oauth2ClientRecord> found = cache.get(clientId);
        if (found.isEmpty() || !found.get().enabled()) {
            return null;
        }
        return toModel(found.get());
    }

    @Override
    public List<String> getHigherScopeList() {
        return scopeRegistry.higherScopeNames();
    }

    @Override
    public List<String> getLowerScopeList() {
        return scopeRegistry.lowerScopeNames();
    }

    private SaClientModel toModel(Oauth2ClientRecord record) {
        SaClientModel model = new SaClientModel();
        model.setClientId(record.getClientId());
        model.setClientSecret(cipher.decrypt(record.getClientSecret()));
        model.setContractScopes(record.getContractScopes());
        model.setAllowRedirectUris(record.getAllowRedirectUris());
        model.setAllowGrantTypes(record.getAllowGrantTypes());
        model.setSubjectId(record.getSubjectId());
        model.setAccessTokenTimeout(record.getAccessTokenTimeout() > 0
                ? record.getAccessTokenTimeout() : properties.getAccessTokenTimeoutSeconds());
        model.setRefreshTokenTimeout(record.getRefreshTokenTimeout() > 0
                ? record.getRefreshTokenTimeout() : properties.getRefreshTokenTimeoutSeconds());
        model.setClientTokenTimeout(record.getClientTokenTimeout() > 0
                ? record.getClientTokenTimeout() : properties.getClientTokenTimeoutSeconds());
        model.setMaxAccessTokenCount(record.getMaxAccessTokenCount() > 0 ? record.getMaxAccessTokenCount() : -1);
        model.setMaxRefreshTokenCount(record.getMaxRefreshTokenCount() > 0 ? record.getMaxRefreshTokenCount() : -1);
        model.setMaxClientTokenCount(record.getMaxClientTokenCount() > 0 ? record.getMaxClientTokenCount() : -1);
        model.setIsNewRefresh(record.isNewRefresh());
        model.setIsAutoConfirm(record.isAutoConfirm());
        return model;
    }
}
