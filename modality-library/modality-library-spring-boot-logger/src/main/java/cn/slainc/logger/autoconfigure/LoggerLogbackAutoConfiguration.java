package cn.slainc.logger.autoconfigure;

import ch.qos.logback.classic.turbo.TurboFilter;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.logger.logback.LogbackConfigurer;
import cn.slainc.logger.logback.LogbackLevelManager;
import cn.slainc.logger.logback.SamplingTurboFilter;
import cn.slainc.logger.logback.TenantLevelTurboFilter;
import cn.slainc.logger.logback.TurboFilterRegistrar;
import cn.slainc.logger.mask.LogMasker;
import cn.slainc.logger.monitor.LogAlertDispatcher;
import cn.slainc.logger.monitor.LogMetrics;
import cn.slainc.logger.sample.SamplingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
@ConditionalOnProperty(prefix = "modality.logger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoggerLogbackAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LogbackLevelManager.class)
    @ConditionalOnProperty(prefix = "modality.logger.level", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public LogbackLevelManager logbackLevelManager(LoggerProperties properties,
                                                   ObjectProvider<DynamicConfigService> dynamicConfigService) {
        return new LogbackLevelManager(properties.getLevel(), dynamicConfigService);
    }

    @Bean
    @ConditionalOnMissingBean(SamplingTurboFilter.class)
    @ConditionalOnProperty(prefix = "modality.logger.sample", name = "enabled", havingValue = "true")
    public SamplingTurboFilter samplingTurboFilter(LoggerProperties properties, SamplingManager samplingManager) {
        return new SamplingTurboFilter(properties.getSample(), samplingManager);
    }

    @Bean
    @ConditionalOnMissingBean(TenantLevelTurboFilter.class)
    @ConditionalOnProperty(prefix = "modality.logger.level", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public TenantLevelTurboFilter tenantLevelTurboFilter(LogbackLevelManager levelManager) {
        return new TenantLevelTurboFilter(levelManager);
    }

    @Bean
    @ConditionalOnMissingBean(TurboFilterRegistrar.class)
    public TurboFilterRegistrar turboFilterRegistrar(ObjectProvider<TurboFilter> filters) {
        return new TurboFilterRegistrar(filters.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(LogbackConfigurer.class)
    @ConditionalOnProperty(prefix = "modality.logger.json", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public LogbackConfigurer logbackConfigurer(LoggerProperties properties, LogMasker masker,
                                               ObjectProvider<LogMetrics> metrics,
                                               ObjectProvider<LogAlertDispatcher> alertDispatcher) {
        return new LogbackConfigurer(properties, masker, metrics.getIfAvailable(),
                alertDispatcher.getIfAvailable());
    }
}
