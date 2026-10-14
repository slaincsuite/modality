package cn.slainc.modules.open.autoconfigure;

import cn.slainc.modules.open.config.OpenConfigReader;
import cn.slainc.modules.open.excel.ConsentExportService;
import cn.slainc.modules.open.listener.OpenEventBridge;
import cn.slainc.modules.open.service.OpenAppAdminService;
import cn.slainc.modules.open.service.OpenConsentAdminService;
import cn.slainc.modules.open.service.OpenPortalService;
import cn.slainc.modules.open.service.OpenStatsService;
import cn.slainc.modules.open.store.ConsentQueryDao;
import cn.slainc.modules.open.store.JdbcAppLogStore;
import cn.slainc.modules.open.web.OpenAppAdminController;
import cn.slainc.modules.open.web.OpenConsentAdminController;
import cn.slainc.modules.open.web.OpenPortalController;
import cn.slainc.modules.open.web.OpenScopeAdminController;
import cn.slainc.modules.open.web.OpenStatsController;
import cn.slainc.modules.open.web.OpenUserConsentController;
import cn.slainc.openapi.spi.AppService;
import cn.slainc.openapi.spi.ConsentService;
import cn.slainc.openapi.spi.CredentialService;
import cn.slainc.openapi.spi.CredentialStore;
import cn.slainc.openapi.spi.ScopeService;
import cn.slainc.web.websocket.WebSocketPusher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration(afterName = {"cn.slainc.openapi.autoconfigure.OpenApiAutoConfiguration",
        "cn.slainc.modules.open.autoconfigure.OpenStoreAutoConfiguration"})
@ConditionalOnBean(AppService.class)
@ConditionalOnProperty(prefix = "modality.open", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(OpenModuleProperties.class)
@Import({OpenAppAdminController.class, OpenScopeAdminController.class, OpenConsentAdminController.class,
        OpenStatsController.class, OpenPortalController.class, OpenUserConsentController.class})
public class OpenModuleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenConfigReader openConfigReader(ObjectProvider<cn.slainc.core.config.DynamicConfigService> configService,
                                             OpenModuleProperties properties) {
        return new OpenConfigReader(configService, properties.getApp().getCategories());
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenAppAdminService openAppAdminService(AppService appService,
                                                   cn.slainc.openapi.spi.AppStore appStore,
                                                   CredentialService credentialService,
                                                   ConsentService consentService,
                                                   ObjectProvider<JdbcAppLogStore> appLogStore) {
        return new OpenAppAdminService(appService, appStore, credentialService, consentService, appLogStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenPortalService openPortalService(AppService appService, ScopeService scopeService,
                                               CredentialService credentialService, ConsentService consentService,
                                               OpenConfigReader configReader, OpenAppAdminService adminService) {
        return new OpenPortalService(appService, scopeService, credentialService, consentService, configReader,
                adminService);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenConsentAdminService openConsentAdminService(ConsentService consentService,
                                                           ObjectProvider<ConsentQueryDao> queryDao,
                                                           WebSocketPusher pusher) {
        return new OpenConsentAdminService(consentService, queryDao, pusher);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenStatsService openStatsService(AppService appService, ConsentService consentService,
                                             CredentialService credentialService,
                                             ObjectProvider<ConsentQueryDao> queryDao,
                                             ObjectProvider<cn.slainc.apikey.admin.ApiKeyStatsService> apiKeyStats,
                                             OpenModuleProperties properties) {
        return new OpenStatsService(appService, consentService, credentialService, queryDao, apiKeyStats,
                properties.getStats().getTrendDays(), properties.getStats().getTopN());
    }

    @Bean
    @ConditionalOnMissingBean
    public ConsentExportService openConsentExportService(OpenConsentAdminService consentAdminService,
                                                         AppService appService) {
        return new ConsentExportService(consentAdminService, appService);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenEventBridge openEventBridge(CredentialStore credentialStore,
                                           ApplicationEventPublisher publisher) {
        return new OpenEventBridge(credentialStore, publisher);
    }
}
