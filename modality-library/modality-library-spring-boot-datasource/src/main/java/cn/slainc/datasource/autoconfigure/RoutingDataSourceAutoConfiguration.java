package cn.slainc.datasource.autoconfigure;

import cn.slainc.datasource.config.DataSourcePoolCustomizer;
import cn.slainc.datasource.crypto.DataSourcePasswordEncryptor;
import cn.slainc.datasource.definition.DataSourceDefinitionRepository;
import cn.slainc.datasource.definition.InMemoryDataSourceDefinitionRepository;
import cn.slainc.datasource.definition.JdbcDataSourceDefinitionRepository;
import cn.slainc.datasource.failover.DataSourceFailoverManager;
import cn.slainc.datasource.health.DataSourceHealthChecker;
import cn.slainc.datasource.health.DataSourceHealthScheduler;
import cn.slainc.datasource.manager.DataSourceRegistrar;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import cn.slainc.datasource.migration.DataSourceMigrationTool;
import cn.slainc.datasource.properties.DataSourceProperties;
import cn.slainc.datasource.routing.RoutingDataSource;
import cn.slainc.datasource.routing.TransactionMasterAspect;
import cn.slainc.datasource.transaction.CrossDataSourceTransactionTemplate;
import cn.slainc.datasource.transaction.TransactionHelper;
import cn.slainc.datasource.transaction.TransactionLogAspect;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration(before = DynamicDataSourceAutoConfiguration.class)
@ConditionalOnClass({DynamicRoutingDataSource.class, DataSource.class})
@ConditionalOnProperty(prefix = "modality.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "spring.datasource.dynamic", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DataSourceProperties.class)
public class RoutingDataSourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public RoutingDataSource dataSource(List<DynamicDataSourceProvider> providers,
                                                DynamicDataSourceProperties properties) {
        RoutingDataSource dataSource = new RoutingDataSource(providers);
        dataSource.setPrimary(properties.getPrimary());
        dataSource.setStrict(properties.getStrict());
        dataSource.setStrategy(properties.getStrategy());
        dataSource.setP6spy(properties.getP6spy());
        dataSource.setSeata(properties.getSeata());
        dataSource.setGraceDestroy(properties.getGraceDestroy());
        return dataSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourcePoolCustomizer dataSourcePoolCustomizer(DataSourceProperties properties) {
        return new DataSourcePoolCustomizer(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourcePasswordEncryptor dataSourcePasswordEncryptor(DataSourceProperties properties) {
        return new DataSourcePasswordEncryptor(properties.getEncrypt().isEnabled(),
                properties.getEncrypt().getKey(), properties.getEncrypt().getKeyId());
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourceDefinitionRepository dataSourceDefinitionRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return new InMemoryDataSourceDefinitionRepository();
        }
        JdbcDataSourceDefinitionRepository repository = new JdbcDataSourceDefinitionRepository(jdbcTemplate);
        try {
            repository.initialize();
        } catch (Exception exception) {
            return new InMemoryDataSourceDefinitionRepository();
        }
        return repository;
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicDataSourceManager dynamicDataSourceManager(RoutingDataSource routingDataSource,
                                                             DefaultDataSourceCreator dataSourceCreator,
                                                             DataSourceDefinitionRepository repository,
                                                             DataSourcePasswordEncryptor encryptor,
                                                             DataSourceProperties properties) {
        return new DynamicDataSourceManager(routingDataSource, dataSourceCreator, repository, encryptor, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourceRegistrar dataSourceRegistrar(DynamicDataSourceManager manager) {
        return new DataSourceRegistrar(manager);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourceHealthChecker dataSourceHealthChecker(DataSourceProperties properties) {
        return new DataSourceHealthChecker(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourceFailoverManager dataSourceFailoverManager(DynamicDataSourceManager manager,
                                                               DataSourceHealthChecker healthChecker,
                                                               DataSourceProperties properties,
                                                               ApplicationEventPublisher eventPublisher) {
        return new DataSourceFailoverManager(manager, healthChecker, properties, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourceHealthScheduler dataSourceHealthScheduler(DynamicDataSourceManager manager,
                                                               DataSourceHealthChecker healthChecker,
                                                               DataSourceFailoverManager failoverManager,
                                                               DataSourceProperties properties) {
        return new DataSourceHealthScheduler(manager, healthChecker, failoverManager, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSourceMigrationTool dataSourceMigrationTool(DynamicDataSourceManager manager) {
        return new DataSourceMigrationTool(manager);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionHelper transactionHelper(PlatformTransactionManager transactionManager) {
        return new TransactionHelper(transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public CrossDataSourceTransactionTemplate crossDataSourceTransactionTemplate(
            DynamicDataSourceManager manager, PlatformTransactionManager transactionManager) {
        return new CrossDataSourceTransactionTemplate(manager, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.datasource", name = "transaction-log-enabled", havingValue = "true")
    public TransactionLogAspect transactionLogAspect() {
        return new TransactionLogAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.datasource.dynamic.aop", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TransactionMasterAspect transactionMasterAspect(DynamicDataSourceProperties properties) {
        return new TransactionMasterAspect(properties.getPrimary());
    }
}
