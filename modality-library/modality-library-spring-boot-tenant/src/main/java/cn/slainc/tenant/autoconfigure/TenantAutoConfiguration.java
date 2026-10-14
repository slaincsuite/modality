package cn.slainc.tenant.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.datasource.manager.DynamicDataSourceManager;
import cn.slainc.core.base.MybatisBaseEntity;
import cn.slainc.mybatis.tenant.DefaultTenantLineHandler;
import cn.slainc.mybatis.properties.MybatisProperties;
import cn.slainc.redis.cache.CacheInvalidationPublisher;
import cn.slainc.tenant.context.TenantConsistencyFilter;
import cn.slainc.tenant.context.TenantFilter;
import cn.slainc.tenant.context.TenantStatusChecker;
import cn.slainc.tenant.domain.Tenant;
import cn.slainc.tenant.isolation.DatabaseIsolationTemplate;
import cn.slainc.tenant.isolation.DatabaseTenantIsolationHandler;
import cn.slainc.tenant.isolation.RowTenantIsolationHandler;
import cn.slainc.tenant.isolation.SchemaTenantIsolationHandler;
import cn.slainc.tenant.isolation.TenantIsolationDispatcher;
import cn.slainc.tenant.isolation.TenantIsolationStrategyResolver;
import cn.slainc.tenant.lifecycle.DefaultTenantInitializer;
import cn.slainc.tenant.lifecycle.TenantDataTemplate;
import cn.slainc.tenant.lifecycle.TenantInitializer;
import cn.slainc.tenant.lifecycle.TenantLifecycleOptions;
import cn.slainc.tenant.lifecycle.TenantLifecycleService;
import cn.slainc.tenant.dao.TenantMapper;
import cn.slainc.tenant.metrics.TenantMetricsRecorder;
import cn.slainc.tenant.quota.TenantQuotaService;
import cn.slainc.tenant.resolver.DomainTenantResolver;
import cn.slainc.tenant.resolver.HeaderTenantResolver;
import cn.slainc.tenant.resolver.ParamTenantResolver;
import cn.slainc.tenant.resolver.PathTenantResolver;
import cn.slainc.tenant.resolver.TenantLookup;
import cn.slainc.tenant.resolver.TenantResolver;
import cn.slainc.tenant.resolver.TenantResolverComposite;
import cn.slainc.tenant.lookup.TenantCacheNotifier;
import cn.slainc.tenant.lookup.TenantService;
import cn.slainc.tenant.support.TenantTableWhitelist;
import cn.slainc.web.filter.FilterOrder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@AutoConfigureAfter(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({TenantProperties.class, TenantStatusCheckProperties.class})
public class TenantAutoConfiguration {

    private static final String COMPOSITE_BEAN_NAME = "tenantResolverComposite";

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.resolver.header", name = "enabled", havingValue = "true")
    public HeaderTenantResolver headerTenantResolver(TenantProperties properties,
                                                     ObjectProvider<TenantLookup> lookup) {
        return new HeaderTenantResolver(properties.getResolver().getHeader().getName(), lookup.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.resolver.domain", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public DomainTenantResolver domainTenantResolver(TenantProperties properties,
                                                     ObjectProvider<TenantLookup> lookup) {
        return new DomainTenantResolver(properties.getResolver().getDomain().getRootDomain(),
                properties.getResolver().getDomain().getIgnoredSubdomains(), lookup.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.resolver.path", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public PathTenantResolver pathTenantResolver(TenantProperties properties,
                                                 ObjectProvider<TenantLookup> lookup) {
        return new PathTenantResolver(properties.getResolver().getPath().getPrefix(),
                properties.getResolver().getPath().getIndex(), lookup.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.resolver.param", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ParamTenantResolver paramTenantResolver(TenantProperties properties,
                                                   ObjectProvider<TenantLookup> lookup) {
        return new ParamTenantResolver(properties.getResolver().getParam().getName(), lookup.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantResolverComposite tenantResolverComposite(ConfigurableListableBeanFactory beanFactory) {
        List<TenantResolver> resolvers = new ArrayList<>();
        for (String name : beanFactory.getBeanNamesForType(TenantResolver.class)) {
            // 组合器自身也实现了 TenantResolver，按名称排除以避免自引用
            if (COMPOSITE_BEAN_NAME.equals(name)) {
                continue;
            }
            resolvers.add(beanFactory.getBean(name, TenantResolver.class));
        }
        return new TenantResolverComposite(resolvers);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "modality.tenant.filter", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public TenantFilter tenantFilter(TenantResolverComposite resolvers,
                                     ObjectProvider<TenantMetricsRecorder> metrics,
                                     ObjectProvider<TenantStatusChecker> statusChecker) {
        return new TenantFilter(resolvers, metrics.getIfAvailable(), statusChecker.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "modality.tenant.filter", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<TenantFilter> tenantFilterRegistration(TenantFilter tenantFilter) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(tenantFilter);
        registration.setName("tenantFilter");
        registration.setOrder(FilterOrder.TENANT);
        registration.addUrlPatterns("/*");
        registration.setEnabled(true);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.status-check", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public TenantStatusChecker tenantStatusChecker(ObjectProvider<TenantService> tenantService,
                                                   TenantStatusCheckProperties properties) {
        return new TenantStatusChecker(tenantService.getIfAvailable(), properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.consistency", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public TenantConsistencyFilter tenantConsistencyFilter(
            ObjectProvider<cn.slainc.tenant.resolver.SessionTenantResolver> sessionResolver,
            ObjectProvider<cn.slainc.tenant.resolver.HeaderTenantResolver> headerResolver,
            ObjectProvider<cn.slainc.tenant.resolver.ParamTenantResolver> paramResolver,
            ObjectProvider<cn.slainc.tenant.resolver.PathTenantResolver> pathResolver,
            ObjectProvider<cn.slainc.tenant.resolver.DomainTenantResolver> domainResolver) {
        List<cn.slainc.tenant.resolver.TenantResolver> claims = new ArrayList<>();
        addIfPresent(claims, headerResolver);
        addIfPresent(claims, paramResolver);
        addIfPresent(claims, pathResolver);
        addIfPresent(claims, domainResolver);
        return new TenantConsistencyFilter(sessionResolver.getIfAvailable(), claims);
    }

    @Bean
    @ConditionalOnMissingBean(name = "tenantConsistencyFilterRegistration")
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "modality.tenant.consistency", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public FilterRegistrationBean<TenantConsistencyFilter> tenantConsistencyFilterRegistration(
            TenantConsistencyFilter tenantConsistencyFilter) {
        FilterRegistrationBean<TenantConsistencyFilter> registration =
                new FilterRegistrationBean<>(tenantConsistencyFilter);
        registration.setName("tenantConsistencyFilter");
        registration.setOrder(FilterOrder.TENANT_GUARD);
        registration.addUrlPatterns("/*");
        registration.setEnabled(true);
        return registration;
    }

    private static void addIfPresent(List<cn.slainc.tenant.resolver.TenantResolver> target,
                                     ObjectProvider<? extends cn.slainc.tenant.resolver.TenantResolver> provider) {
        cn.slainc.tenant.resolver.TenantResolver resolver = provider.getIfAvailable();
        if (resolver != null) {
            target.add(resolver);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantTableWhitelist tenantTableWhitelist(TenantProperties properties,
                                                     ObjectProvider<MybatisProperties> mybatisProperties,
                                                     ObjectProvider<DefaultTenantLineHandler> tenantLineHandler) {
        return new TenantTableWhitelist(properties.getIgnoreTables(), properties.getIgnoreTablePrefixes(),
                mybatisProperties.getIfAvailable(), tenantLineHandler.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.isolation", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public RowTenantIsolationHandler rowTenantIsolationHandler() {
        return new RowTenantIsolationHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.isolation.schema", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public SchemaTenantIsolationHandler schemaTenantIsolationHandler(ObjectProvider<JdbcTemplate> jdbcTemplate,
                                                                     TenantProperties properties) {
        return new SchemaTenantIsolationHandler(jdbcTemplate.getIfAvailable(),
                properties.getIsolation().getSchema().getPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    public DatabaseIsolationTemplate databaseIsolationTemplate(TenantProperties properties) {
        TenantProperties.Isolation.Database database = properties.getIsolation().getDatabase();
        return new DatabaseIsolationTemplate(database.getUrlTemplate(), database.getUsernameTemplate(),
                database.getPasswordTemplate(), database.getDriverClassName(), database.getGroupName());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.isolation.database", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public DatabaseTenantIsolationHandler databaseTenantIsolationHandler(
            ObjectProvider<DynamicDataSourceManager> dataSourceManager, DatabaseIsolationTemplate template) {
        return new DatabaseTenantIsolationHandler(dataSourceManager.getIfAvailable(), template);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantIsolationStrategyResolver tenantIsolationStrategyResolver(TenantProperties properties,
                                                                          ObjectProvider<DynamicConfigService> configService) {
        return new TenantIsolationStrategyResolver(properties.getIsolation().getStrategy(),
                configService.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.isolation", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public TenantIsolationDispatcher tenantIsolationDispatcher(
            ObjectProvider<cn.slainc.tenant.isolation.TenantIsolationHandler> handlers,
            TenantIsolationStrategyResolver strategyResolver) {
        return new TenantIsolationDispatcher(handlers.orderedStream().toList(), strategyResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.quota", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public TenantQuotaService tenantQuotaService(TenantProperties properties,
                                                 ObjectProvider<DynamicConfigService> configService,
                                                 ObjectProvider<TenantMetricsRecorder> metrics) {
        return new TenantQuotaService(properties.getQuota().getDefaults(), properties.getQuota().getWarningRatio(),
                configService.getIfAvailable(), metrics.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantDataTemplate tenantDataTemplate(TenantProperties properties) {
        return new TenantDataTemplate(properties.getLifecycle().getData());
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantLifecycleOptions tenantLifecycleOptions(TenantProperties properties) {
        TenantProperties.Lifecycle lifecycle = properties.getLifecycle();
        return new TenantLifecycleOptions(lifecycle.getTrialDays(), lifecycle.getDefaultExpireDays(),
                lifecycle.isInitializeOnCreate(), lifecycle.isRemoveIsolationOnDelete());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "modality.tenant.lifecycle", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public DefaultTenantInitializer defaultTenantInitializer(TenantDataTemplate tenantDataTemplate,
                                                             ObjectProvider<JdbcTemplate> jdbcTemplate,
                                                             ObjectProvider<DynamicConfigService> configService) {
        return new DefaultTenantInitializer(tenantDataTemplate, jdbcTemplate.getIfAvailable(),
                configService.getIfAvailable());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({SqlSessionFactory.class, MapperFactoryBean.class, MybatisBaseEntity.class})
    @ConditionalOnBean(SqlSessionFactory.class)
    @ConditionalOnProperty(prefix = "modality.tenant.service", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    static class TenantPersistenceConfiguration {

        @Bean
        @ConditionalOnMissingBean(TenantMapper.class)
        public MapperFactoryBean<TenantMapper> tenantMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<TenantMapper> factory = new MapperFactoryBean<>(TenantMapper.class);
            factory.setSqlSessionFactory(sqlSessionFactory);
            return factory;
        }

        @Bean
        @ConditionalOnMissingBean(name = "tenantCache")
        public Cache<String, Tenant> tenantCache(TenantProperties properties) {
            TenantProperties.CacheSettings settings = properties.getCache();
            return Caffeine.newBuilder()
                    .maximumSize(settings.getMaximumSize())
                    .expireAfterWrite(settings.getExpireAfterWrite())
                    .build();
        }

        @Bean
        @ConditionalOnMissingBean
        public TenantService tenantService(TenantMapper tenantMapper, Cache<String, Tenant> tenantCache,
                                           ObjectProvider<CacheInvalidationPublisher> publisher) {
            CacheInvalidationPublisher invalidation = publisher.getIfAvailable();
            TenantCacheNotifier notifier = invalidation == null ? TenantCacheNotifier.NOOP
                    : key -> invalidation.publishInvalidate("tenant", key == null ? "" : key);
            return new TenantService(tenantMapper, tenantCache, notifier);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "modality.tenant.lifecycle", name = "enabled", havingValue = "true",
                matchIfMissing = true)
        public TenantLifecycleService tenantLifecycleService(TenantService tenantService,
                                                             TenantLifecycleOptions options,
                                                             ObjectProvider<TenantIsolationDispatcher> dispatcher,
                                                             ObjectProvider<TenantInitializer> initializers) {
            return new TenantLifecycleService(tenantService, dispatcher.getIfAvailable(),
                    initializers.orderedStream().toList(), options);
        }
    }
}
