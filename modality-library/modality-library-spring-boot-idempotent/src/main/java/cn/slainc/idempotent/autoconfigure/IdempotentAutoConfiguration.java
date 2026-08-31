package cn.slainc.idempotent.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.idempotent.aspect.IdempotentAspect;
import cn.slainc.idempotent.key.ParameterFingerprint;
import cn.slainc.idempotent.metrics.IdempotentMetrics;
import cn.slainc.idempotent.metrics.LocalIdempotentMetrics;
import cn.slainc.idempotent.spi.HybridStrategyHandler;
import cn.slainc.idempotent.spi.IdempotentStrategyHandler;
import cn.slainc.idempotent.spi.IdempotentStrategyRegistry;
import cn.slainc.idempotent.spi.ParameterStrategyHandler;
import cn.slainc.idempotent.spi.TokenStrategyHandler;
import cn.slainc.idempotent.store.IdempotentStore;
import cn.slainc.idempotent.support.IdempotentContextFactory;
import cn.slainc.idempotent.support.IdempotentExecutor;
import cn.slainc.idempotent.support.IdempotentResultCodec;
import cn.slainc.idempotent.support.IdempotentSettingsResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {IdempotentMetricsAutoConfiguration.class, IdempotentRedisStoreAutoConfiguration.class,
        IdempotentDatabaseStoreAutoConfiguration.class, IdempotentLocalStoreAutoConfiguration.class,
        IdempotentMissingStoreAutoConfiguration.class})
@EnableConfigurationProperties(IdempotentProperties.class)
@ConditionalOnProperty(prefix = IdempotentProperties.PREFIX, name = "enabled", matchIfMissing = true)
public class IdempotentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ParameterFingerprint.class)
    public ParameterFingerprint parameterFingerprint() {
        return new ParameterFingerprint();
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentResultCodec.class)
    public IdempotentResultCodec idempotentResultCodec() {
        return new IdempotentResultCodec();
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentSettingsResolver.class)
    public IdempotentSettingsResolver idempotentSettingsResolver(IdempotentProperties properties,
                                                                ObjectProvider<DynamicConfigService> dynamicConfigService) {
        return new IdempotentSettingsResolver(properties, dynamicConfigService);
    }

    @Bean
    @ConditionalOnMissingBean(ParameterStrategyHandler.class)
    public ParameterStrategyHandler parameterStrategyHandler(ParameterFingerprint parameterFingerprint,
                                                             IdempotentProperties properties) {
        return new ParameterStrategyHandler(parameterFingerprint, properties.getKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean(TokenStrategyHandler.class)
    public TokenStrategyHandler tokenStrategyHandler(IdempotentProperties properties) {
        return new TokenStrategyHandler(properties.getKeyPrefix(), properties.getToken().isBindUser());
    }

    @Bean
    @ConditionalOnMissingBean(HybridStrategyHandler.class)
    public HybridStrategyHandler hybridStrategyHandler(ParameterFingerprint parameterFingerprint,
                                                       IdempotentProperties properties) {
        return new HybridStrategyHandler(parameterFingerprint, properties.getKeyPrefix(),
                properties.getToken().isBindUser());
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentStrategyRegistry.class)
    public IdempotentStrategyRegistry idempotentStrategyRegistry(
            ObjectProvider<IdempotentStrategyHandler> handlers) {
        return new IdempotentStrategyRegistry(handlers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentExecutor.class)
    public IdempotentExecutor idempotentExecutor(IdempotentStore idempotentStore,
                                                 IdempotentStrategyRegistry idempotentStrategyRegistry,
                                                 IdempotentSettingsResolver idempotentSettingsResolver,
                                                 IdempotentResultCodec idempotentResultCodec,
                                                 ObjectProvider<IdempotentMetrics> metrics) {
        return new IdempotentExecutor(idempotentStore, idempotentStrategyRegistry, idempotentSettingsResolver,
                idempotentResultCodec, metrics);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentContextFactory.class)
    public IdempotentContextFactory idempotentContextFactory(IdempotentProperties properties) {
        return new IdempotentContextFactory(properties);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentAspect.class)
    public IdempotentAspect idempotentAspect(IdempotentExecutor idempotentExecutor,
                                             IdempotentContextFactory idempotentContextFactory,
                                             IdempotentProperties properties) {
        return new IdempotentAspect(idempotentExecutor, idempotentContextFactory, properties);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentMetrics.class)
    public LocalIdempotentMetrics localIdempotentMetrics() {
        return new LocalIdempotentMetrics();
    }
}
