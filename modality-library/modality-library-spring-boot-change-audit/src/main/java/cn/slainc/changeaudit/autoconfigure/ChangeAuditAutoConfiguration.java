package cn.slainc.changeaudit.autoconfigure;

import cn.slainc.changeaudit.spi.FieldDiffEngine;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeAuditAutoConfiguration.class);

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
            // 走到这里说明 store 非 jdbc 或缺少 JDBC 能力，必须留痕，避免审计静默失效
            LOGGER.warn("变更审计未装配落库实现（store 非 jdbc 或缺少 JDBC 依赖），本次启动按空实现处理：不再写入变更记录");
            recorder = new NoopChangeRecorder();
        }
        if (!reader.async()) {
            return recorder;
        }
        ThreadPoolTaskExecutor executor = executorProvider.getIfAvailable();
        return executor == null ? recorder : new AsyncChangeRecorder(recorder, executor);
    }
}
