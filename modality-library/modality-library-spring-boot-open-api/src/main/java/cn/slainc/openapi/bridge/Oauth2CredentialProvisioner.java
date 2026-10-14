package cn.slainc.openapi.bridge;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.openapi.domain.App;
import cn.slainc.openapi.domain.AppCredential;
import cn.slainc.openapi.domain.AppType;
import cn.slainc.openapi.spi.CredentialProvisioner;
import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.oauth2.client.Oauth2ClientRecord;
import org.springframework.beans.factory.ObjectProvider;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

public class Oauth2CredentialProvisioner implements CredentialProvisioner {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectProvider<ClientAdminService> clientAdminService;

    public Oauth2CredentialProvisioner(ObjectProvider<ClientAdminService> clientAdminService) {
        this.clientAdminService = clientAdminService;
    }

    @Override
    public boolean supports(App app) {
        return app.getAppType() != AppType.SERVER
                && clientAdminService != null
                && clientAdminService.getIfAvailable() != null;
    }

    @Override
    public AppCredential issue(App app) {
        ClientAdminService service = available();
        Oauth2ClientRecord record = new Oauth2ClientRecord();
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        record.setClientId("cli_" + HexFormat.of().formatHex(bytes));
        String plainSecret = GeneratedCredentialProvisioner.randomSecret();
        record.setClientSecret(plainSecret);
        record.setClientName(app.getName());
        record.setContractScopes(app.getScopes());
        record.setAllowRedirectUris(app.getRedirectUris());
        record.setAllowGrantTypes(grantTypesOf(app.getAppType()));
        record.setSubjectId(app.getOwnerId());
        record.setRemark("开放平台应用 " + app.getAppId());
        service.register(record);
        AppCredential credential = new AppCredential();
        credential.setClientId(record.getClientId());
        credential.setClientSecret(plainSecret);
        credential.setType(AppCredential.Type.OAUTH2);
        return credential;
    }

    @Override
    public AppCredential rotate(AppCredential existing, App app, long graceMillis) {
        ClientAdminService service = available();
        Oauth2ClientRecord rotated = service.rotateSecret(existing.getClientId(), graceMillis);
        AppCredential credential = new AppCredential();
        credential.setClientId(rotated.getClientId());
        credential.setClientSecret(rotated.getClientSecret());
        credential.setType(AppCredential.Type.OAUTH2);
        credential.setPreviousSecretExpireAt(rotated.getPreviousSecretExpireAt());
        return credential;
    }

    @Override
    public void changeStatus(String clientId, boolean enabled) {
        available().changeStatus(clientId, enabled);
    }

    private ClientAdminService available() {
        ClientAdminService service = clientAdminService == null ? null : clientAdminService.getIfAvailable();
        if (service == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE,
                    "oauth2 模块的 ClientAdminService 不可用，无法签发 OAuth2 凭证");
        }
        return service;
    }

    private List<String> grantTypesOf(AppType appType) {
        if (appType == AppType.SERVER) {
            return List.of("client_credentials", "refresh_token");
        }
        return List.of("authorization_code", "refresh_token");
    }
}
