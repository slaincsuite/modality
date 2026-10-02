package cn.slainc.modules.infra.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.modules.infra.context.CurrentUser;
import cn.slainc.modules.infra.excel.DictTransferService;
import cn.slainc.modules.infra.service.ConfigAdminService;
import cn.slainc.modules.infra.service.DictService;
import cn.slainc.modules.infra.service.NoticeService;
import cn.slainc.modules.infra.store.ConfigHistoryCipher;
import cn.slainc.modules.infra.store.ConfigHistoryStore;
import cn.slainc.modules.infra.web.ConfigAdminController;
import cn.slainc.modules.infra.web.DictController;
import cn.slainc.modules.infra.web.NoticeController;
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

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration")
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "modality.infra", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(InfraModuleProperties.class)
@Import({ConfigAdminController.class, DictController.class, NoticeController.class})
public class InfraCoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(InfraCoreAutoConfiguration.class);

    @Bean(initMethod = "init")
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
}

    @Bean
    @ConditionalOnMissingBean
    public DictService dictService(JdbcTemplate jdbc) {
        return new DictService(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    public DictTransferService dictTransferService(DictService dictService) {
        return new DictTransferService(dictService);
    }
}

    @Bean
    @ConditionalOnMissingBean
    public NoticeService noticeService(JdbcTemplate jdbc) {
        return new NoticeService(jdbc);
    }
}
