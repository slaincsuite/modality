package cn.slainc.openapi.bridge;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.apikey.admin.ApiKeyAdminService;
import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppCredential;
import cn.slainc.openapi.domain.AppType;
import cn.slainc.openapi.spi.CredentialProvisioner;
import cn.dev33.satoken.apikey.model.ApiKeyModel;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

public class ApiKeyCredentialProvisioner implements CredentialProvisioner {

    private final ObjectProvider<ApiKeyAdminService> apiKeyAdminService;

    public ApiKeyCredentialProvisioner(ObjectProvider<ApiKeyAdminService> apiKeyAdminService) {
        this.apiKeyAdminService = apiKeyAdminService;
    }

    @Override
    public boolean supports(App app) {
        return app.getAppType() == AppType.SERVER && available() != null;
    }

    @Override
    public AppCredential issue(App app) {
        ApiKeyModel model = available().issue(app.getOwnerId(), app.getName(), app.getScopes(), null,
                Map.of("appId", app.getAppId()));
        return credentialOf(model, null);
    }

    @Override
    public AppCredential rotate(AppCredential existing, App app, long graceMillis) {
        long graceHours = Math.max(graceMillis / 3600000L, 0);
        ApiKeyModel model = available().rotate(existing.getClientId(), graceHours);
        return credentialOf(model, graceMillis <= 0 ? null : System.currentTimeMillis() + graceMillis);
    }

    @Override
    public void changeStatus(String clientId, boolean enabled) {
        if (enabled) {
            available().enable(clientId);
        } else {
            available().disable(clientId);
        }
    }

    private ApiKeyAdminService available() {
        ApiKeyAdminService service = apiKeyAdminService == null ? null : apiKeyAdminService.getIfAvailable();
        if (service == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE,
                    "api-key 模块的 ApiKeyAdminService 不可用，无法签发 API Key 凭证");
        }
        return service;
    }

    private AppCredential credentialOf(ApiKeyModel model, Long previousExpireAt) {
        AppCredential credential = new AppCredential();
        credential.setClientId(model.getApiKey());
        credential.setClientSecret(model.getApiKey());
        credential.setType(AppCredential.Type.API_KEY);
        credential.setPreviousSecretExpireAt(previousExpireAt);
        return credential;
    }
}
