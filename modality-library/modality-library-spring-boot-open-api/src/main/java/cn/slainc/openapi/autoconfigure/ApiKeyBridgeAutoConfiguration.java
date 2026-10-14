package cn.slainc.openapi.autoconfigure;

import cn.slainc.apikey.admin.ApiKeyAdminService;
import cn.slainc.openapi.bridge.ApiKeyCredentialProvisioner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "cn.slainc.apikey.admin.ApiKeyAdminService")
@ConditionalOnProperty(prefix = "modality.open-api", name = "api-key-bridge-enabled", matchIfMissing = true)
public class ApiKeyBridgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ApiKeyCredentialProvisioner.class)
    public ApiKeyCredentialProvisioner openApiApiKeyCredentialProvisioner(
            ObjectProvider<ApiKeyAdminService> apiKeyAdminService) {
        return new ApiKeyCredentialProvisioner(apiKeyAdminService);
    }
}
