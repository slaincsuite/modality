package cn.slainc.openapi.autoconfigure;

import cn.slainc.openapi.bridge.GeneratedCredentialProvisioner;
import cn.slainc.openapi.spi.AppService;
import cn.slainc.openapi.spi.AppStore;
import cn.slainc.openapi.spi.ConsentService;
import cn.slainc.openapi.spi.ConsentStore;
import cn.slainc.openapi.spi.CredentialProvisioner;
import cn.slainc.openapi.spi.CredentialService;
import cn.slainc.openapi.spi.CredentialStore;
import cn.slainc.openapi.spi.ScopeService;
import cn.slainc.openapi.spi.ScopeStore;
import cn.slainc.openapi.support.DefaultAppService;
import cn.slainc.openapi.support.DefaultConsentService;
import cn.slainc.openapi.support.DefaultCredentialService;
import cn.slainc.openapi.support.DefaultScopeService;
import cn.slainc.openapi.support.MemoryStores;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "modality.open-api", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AppStore.class)
    public MemoryStores.InMemoryAppStore openApiAppStore() {
        return new MemoryStores.InMemoryAppStore();
    }

    @Bean
    @ConditionalOnMissingBean(ScopeStore.class)
    public MemoryStores.InMemoryScopeStore openApiScopeStore() {
        return new MemoryStores.InMemoryScopeStore();
    }

    @Bean
    @ConditionalOnMissingBean(ConsentStore.class)
    public MemoryStores.InMemoryConsentStore openApiConsentStore() {
        return new MemoryStores.InMemoryConsentStore();
    }

    @Bean
    @ConditionalOnMissingBean(CredentialStore.class)
    public MemoryStores.InMemoryCredentialStore openApiCredentialStore() {
        return new MemoryStores.InMemoryCredentialStore();
    }

    @Bean
    @ConditionalOnMissingBean(AppService.class)
    public DefaultAppService openApiAppService(AppStore appStore, ApplicationEventPublisher eventPublisher) {
        return new DefaultAppService(appStore, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(ScopeService.class)
    public DefaultScopeService openApiScopeService(ScopeStore scopeStore) {
        return new DefaultScopeService(scopeStore);
    }

    @Bean
    @ConditionalOnMissingBean(ConsentService.class)
    public DefaultConsentService openApiConsentService(ConsentStore consentStore, AppStore appStore,
                                                       ScopeStore scopeStore,
                                                       ApplicationEventPublisher eventPublisher) {
        return new DefaultConsentService(consentStore, appStore, scopeStore, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean(CredentialService.class)
    public DefaultCredentialService openApiCredentialService(AppStore appStore, CredentialStore credentialStore,
                                                             List<CredentialProvisioner> provisioners) {
        return new DefaultCredentialService(appStore, credentialStore, provisioners);
    }

    @Bean
    @ConditionalOnMissingBean(GeneratedCredentialProvisioner.class)
    @ConditionalOnProperty(prefix = "modality.open-api", name = "generated-credential-enabled", matchIfMissing = true)
    public GeneratedCredentialProvisioner openApiGeneratedCredentialProvisioner() {
        return new GeneratedCredentialProvisioner();
    }
}
