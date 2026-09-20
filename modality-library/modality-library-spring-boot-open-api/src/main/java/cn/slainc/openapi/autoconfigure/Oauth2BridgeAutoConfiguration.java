package cn.slainc.openapi.autoconfigure;

import cn.slainc.oauth2.client.ClientAdminService;
import cn.slainc.openapi.bridge.Oauth2CredentialProvisioner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "cn.slainc.oauth2.client.ClientAdminService")
@ConditionalOnProperty(prefix = "modality.open-api", name = "oauth2-bridge-enabled", matchIfMissing = true)
public class Oauth2BridgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Oauth2CredentialProvisioner.class)
    public Oauth2CredentialProvisioner openApiOauth2CredentialProvisioner(
            ObjectProvider<ClientAdminService> clientAdminService) {
        return new Oauth2CredentialProvisioner(clientAdminService);
    }
}
