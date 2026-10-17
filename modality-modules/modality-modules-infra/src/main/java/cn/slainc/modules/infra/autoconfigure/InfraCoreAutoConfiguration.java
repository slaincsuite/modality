package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.modules.infra.service.recycle.RecycleBinService;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.modules.infra.service.config.ConfigAdminService;
import cn.slainc.modules.infra.store.ConfigHistoryCipher;
import cn.slainc.modules.infra.store.ConfigHistoryStore;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.service.config.DictService;
import cn.slainc.modules.infra.excel.DictTransferService;
import cn.slainc.modules.infra.service.notify.NoticeService;
import cn.slainc.modules.infra.service.notify.NotifyReceiverService;
import cn.slainc.modules.infra.service.notify.NotifyService;
import cn.slainc.modules.infra.service.notify.NotifySubscriptionService;
import cn.slainc.modules.infra.service.notify.NotifyWebhookClient;
import cn.slainc.modules.infra.store.ApiKeyQueryStore;
import cn.slainc.modules.infra.store.ApiKeyRequestMetricsStore;
import cn.slainc.modules.infra.support.ApiKeyMetricsFilter;
import cn.slainc.modules.infra.service.apikey.ApiKeyManageService;
import cn.slainc.modules.infra.web.apikey.ApiKeyManageController;
import cn.slainc.modules.infra.web.apikey.UserApiKeyController;
import cn.slainc.modules.infra.web.config.ConfigAdminController;
import cn.slainc.modules.infra.web.config.DictController;
import cn.slainc.modules.infra.web.notify.NoticeController;
import cn.slainc.modules.infra.web.notify.NotifyAdminController;
import cn.slainc.modules.infra.web.notify.NotifyReceiverController;
import cn.slainc.modules.infra.web.notify.NotifySubscriptionController;
import cn.slainc.modules.infra.web.notify.NotifyUserController;
import cn.slainc.apikey.support.ApiKeyAdminService;
import cn.slainc.apikey.config.ApiKeyProperties;
import cn.slainc.apikey.support.ApiKeyStatsService;
import cn.slainc.redis.cache.CacheInvalidationListener;
import cn.slainc.redis.cache.CacheInvalidationPublisher;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.slainc.web.websocket.WebSocketPusher;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "modality.infra", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(InfraModuleProperties.class)
@Import({ApiKeyManageController.class, ConfigAdminController.class, DictController.class, NoticeController.class,
        NotifyAdminController.class, NotifyReceiverController.class, NotifySubscriptionController.class,
        NotifyUserController.class, UserApiKeyController.class})
public class InfraCoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InfraCoreAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(InfraSchemaInitializer.class)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "modality.infra", name = "auto-create-table", matchIfMissing = true)
    public InfraSchemaInitializer infraSchemaInitializer(DataSource dataSource) {
        return new InfraSchemaInitializer(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public CurrentUser infraCurrentUser() {
        return new CurrentUser();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfigHistoryCipher configHistoryCipher(InfraModuleProperties properties) {
        if (properties.getConfigHistoryKey() == null || properties.getConfigHistoryKey().isBlank()) {
            log.warn("未配置 modality.infra.config-history-key，配置历史将使用固定默认密钥加密，生产环境必须显式配置");
        }
        return new ConfigHistoryCipher(properties.getConfigHistoryKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfigHistoryStore configHistoryStore(JdbcTemplate jdbc, ConfigHistoryCipher cipher) {
        return new ConfigHistoryStore(jdbc, cipher);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConfigAdminService configAdminService(JdbcTemplate jdbc,
                                                 ObjectProvider<DynamicConfigService> configService,
                                                 ConfigHistoryStore historyStore) {
        return new ConfigAdminService(jdbc, configService, historyStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public DictService dictService(JdbcTemplate jdbc,
                                   ObjectProvider<CacheInvalidationPublisher> invalidationPublisher,
                                   ObjectProvider<CacheInvalidationListener> invalidationListener,
                                   InfraModuleProperties properties) {
        return new DictService(jdbc, properties.getDictCacheTtl(), invalidationPublisher.getIfAvailable(),
                invalidationListener.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyQueryStore apiKeyQueryStore(JdbcTemplate jdbc) {
        return new ApiKeyQueryStore(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyManageService apiKeyManageService(JdbcTemplate jdbc, ApiKeyQueryStore queryStore,
                                                   ApiKeyAdminService adminService, ApiKeyStatsService statsService,
                                                   ApiKeyRequestMetricsStore metricsStore) {
        return new ApiKeyManageService(jdbc, queryStore, adminService, statsService, metricsStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiKeyRequestMetricsStore apiKeyRequestMetricsStore(JdbcTemplate jdbc) {
        return new ApiKeyRequestMetricsStore(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.apikey", name = "enabled", matchIfMissing = true)
    public ApiKeyMetricsFilter apiKeyMetricsFilter(ApiKeyProperties properties,
                                                   ApiKeyRequestMetricsStore metricsStore) {
        return new ApiKeyMetricsFilter(properties, metricsStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public DictTransferService dictTransferService(DictService dictService) {
        return new DictTransferService(dictService);
    }

    @Bean
    @ConditionalOnMissingBean
    public NoticeService noticeService(JdbcTemplate jdbc,
                                       ObjectProvider<cn.slainc.modules.infra.service.recycle.RecycleBinService> recycleBinService) {
        return new NoticeService(jdbc, recycleBinService);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyWebhookClient notifyWebhookClient(InfraModuleProperties properties) {
        return new NotifyWebhookClient(properties.getNotify().getConnectTimeoutMillis(),
                properties.getNotify().getReadTimeoutMillis(), properties.getNotify().isAllowInternalWebhook());
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyService notifyService(JdbcTemplate jdbc, NotifyWebhookClient webhookClient,
                                       WebSocketPusher pusher,
                                       InfraModuleProperties properties) {
        return new NotifyService(jdbc, webhookClient, pusher, properties.getNotify().getMaxRetry());
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyReceiverService notifyReceiverService(JdbcTemplate jdbc) {
        return new NotifyReceiverService(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifySubscriptionService notifySubscriptionService(JdbcTemplate jdbc) {
        return new NotifySubscriptionService(jdbc);
    }
}
