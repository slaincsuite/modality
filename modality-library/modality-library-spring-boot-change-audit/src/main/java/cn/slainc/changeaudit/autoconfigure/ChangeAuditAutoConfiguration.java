package cn.slainc.changeaudit.autoconfigure;

import cn.slainc.changeaudit.api.FieldDiffEngine;
import cn.slainc.changeaudit.support.FieldDiffer;
import cn.slainc.changeaudit.support.JaVersFieldDiffer;
import cn.slainc.changeaudit.aspect.ChangeAuditAspect;
import cn.slainc.changeaudit.event.AfterCommitExecutor;
import cn.slainc.changeaudit.event.TransactionAfterCommitExecutor;
import cn.slainc.changeaudit.spi.ChangeRecorder;
import cn.slainc.changeaudit.spi.SnapshotLoader;
import cn.slainc.changeaudit.support.AsyncChangeRecorder;
import cn.slainc.changeaudit.support.NoopChangeRecorder;
import cn.slainc.changeaudit.support.SnapshotLoaderRegistry;
import cn.slainc.core.config.DynamicConfigService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ThreadPoolExecutor;

@AutoConfiguration
@EnableConfigurationProperties(ChangeAuditProperties.class)
@ConditionalOnProperty(prefix = ChangeAuditProperties.PREFIX, name = "enabled", matchIfMissing = true)
public class ChangeAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ChangeAuditConfigReader changeAuditConfigReader(ChangeAuditProperties properties,
                                                           ObjectProvider<DynamicConfigService> dynamicConfigService) {
        return new ChangeAuditConfigReader(properties, dynamicConfigService);
    }

    @Bean
    @ConditionalOnMissingBean(SnapshotLoaderRegistry.class)
    public SnapshotLoaderRegistry snapshotLoaderRegistry(ObjectProvider<SnapshotLoader> loaderProvider) {
        return new SnapshotLoaderRegistry(loaderProvider.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(FieldDiffEngine.class)
    public FieldDiffEngine fieldDiffEngine(ObjectProvider<ObjectMapper> mapperProvider,
                                           ChangeAuditConfigReader reader) {
        FieldDiffer builtin = new FieldDiffer(mapperProvider.getIfUnique(), reader);
        return reader.javersDiffEngine() ? new JaVersFieldDiffer(builtin) : builtin;
    }

    @Bean
    @ConditionalOnMissingBean(AfterCommitExecutor.class)
    public AfterCommitExecutor changeAuditAfterCommitExecutor() {
        return new TransactionAfterCommitExecutor();
    }

    @Bean(name = "changeAuditExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "changeAuditExecutor")
    @ConditionalOnProperty(prefix = ChangeAuditProperties.PREFIX, name = "async", havingValue = "true")
    public ThreadPoolTaskExecutor changeAuditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1024);
        executor.setThreadNamePrefix("modality-change-audit-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(ChangeAuditAspect.class)
    public ChangeAuditAspect changeAuditAspect(ChangeAuditConfigReader reader,
                                               SnapshotLoaderRegistry snapshotLoaderRegistry,
                                               FieldDiffEngine fieldDiffer,
                                               ObjectProvider<ChangeRecorder> recorderProvider,
                                               @Qualifier("changeAuditExecutor")
                                               ObjectProvider<ThreadPoolTaskExecutor> executorProvider,
                                               ObjectProvider<AfterCommitExecutor> afterCommitProvider) {
        AfterCommitExecutor afterCommitExecutor = afterCommitProvider.getIfUnique();
        if (afterCommitExecutor == null) {
            afterCommitExecutor = new TransactionAfterCommitExecutor();
        }
        return new ChangeAuditAspect(reader, snapshotLoaderRegistry, fieldDiffer,
                changeRecorder(reader, recorderProvider, executorProvider), afterCommitExecutor);
    }

    private static ChangeRecorder changeRecorder(ChangeAuditConfigReader reader,
                                                 ObjectProvider<ChangeRecorder> recorderProvider,
                                                 ObjectProvider<ThreadPoolTaskExecutor> executorProvider) {
        ChangeRecorder recorder = recorderProvider.getIfAvailable();
        if (recorder == null) {
            recorder = new NoopChangeRecorder();
        }
        if (!reader.async()) {
            return recorder;
        }
        ThreadPoolTaskExecutor executor = executorProvider.getIfAvailable();
        return executor == null ? recorder : new AsyncChangeRecorder(recorder, executor);
    }
}
