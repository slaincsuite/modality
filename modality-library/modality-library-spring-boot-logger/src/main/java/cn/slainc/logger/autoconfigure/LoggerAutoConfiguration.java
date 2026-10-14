package cn.slainc.logger.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.logger.oplog.AuditLogAspect;
import cn.slainc.logger.oplog.AuditLogDispatcher;
import cn.slainc.logger.oplog.AuditLogStore;
import cn.slainc.logger.oplog.AuditSnapshotProvider;
import cn.slainc.logger.oplog.Slf4jAuditLogStore;
import cn.slainc.logger.context.LoginIdResolver;
import cn.slainc.logger.level.LogLevelManager;
import cn.slainc.logger.level.LoggingStartupRefresher;
import cn.slainc.logger.mask.LogMasker;
import cn.slainc.logger.mask.SensitiveFieldProvider;
import cn.slainc.logger.monitor.InMemoryLogMetrics;
import cn.slainc.logger.monitor.LogAlertDispatcher;
import cn.slainc.logger.monitor.LogAlertHook;
import cn.slainc.logger.monitor.LogMetrics;
import cn.slainc.logger.archive.LogArchiveService;
import cn.slainc.logger.archive.LogCleanupService;
import cn.slainc.logger.archive.LogMaintenanceScheduler;
import cn.slainc.logger.query.LogFileQueryService;
import cn.slainc.logger.sample.SamplingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(LoggerProperties.class)
@ConditionalOnProperty(prefix = "modality.logger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LoggerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LogMasker.class)
    public LogMasker logMasker(LoggerProperties properties, ObjectProvider<SensitiveFieldProvider> providers) {
        return new LogMasker(properties.getMask(), providers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(LoginIdResolver.class)
    public LoginIdResolver loginIdResolver() {
        return LoginIdResolver.USER_CONTEXT;
    }

    @Bean
    @ConditionalOnMissingBean(SamplingManager.class)
    @ConditionalOnProperty(prefix = "modality.logger.sample", name = "enabled", havingValue = "true",
            matchIfMissing = false)
    public SamplingManager samplingManager(LoggerProperties properties,
                                           ObjectProvider<DynamicConfigService> dynamicConfigService) {
        return new SamplingManager(properties.getSample(), dynamicConfigService);
    }

    @Bean
    @ConditionalOnMissingBean(LogMetrics.class)
    public LogMetrics logMetrics() {
        return new InMemoryLogMetrics();
    }

    @Bean
    @ConditionalOnMissingBean(LogAlertDispatcher.class)
    @ConditionalOnProperty(prefix = "modality.logger.monitor", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public LogAlertDispatcher logAlertDispatcher(LoggerProperties properties, LogMetrics metrics,
                                                 ObjectProvider<LogAlertHook> hooks) {
        return new LogAlertDispatcher(properties.getMonitor(), metrics, hooks.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(Slf4jAuditLogStore.class)
    @ConditionalOnProperty(prefix = "modality.logger.audit", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public Slf4jAuditLogStore slf4jAuditLogStore() {
        return new Slf4jAuditLogStore();
    }

    @Bean
    @ConditionalOnMissingBean(AuditLogDispatcher.class)
    @ConditionalOnProperty(prefix = "modality.logger.audit", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public AuditLogDispatcher auditLogDispatcher(LoggerProperties properties,
                                                 ObjectProvider<AuditLogStore> stores) {
        return new AuditLogDispatcher(properties.getAudit(), stores.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(AuditLogAspect.class)
    @ConditionalOnProperty(prefix = "modality.logger.audit", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public AuditLogAspect auditLogAspect(LoggerProperties properties, AuditLogDispatcher dispatcher,
                                         LogMasker masker, ObjectProvider<AuditSnapshotProvider> providers,
                                         LoginIdResolver loginIdResolver) {
        return new AuditLogAspect(properties.getAudit(), dispatcher, masker,
                providers.orderedStream().toList(), loginIdResolver);
    }

    @Bean
    @ConditionalOnMissingBean(LogArchiveService.class)
    @ConditionalOnProperty(prefix = "modality.logger.archive", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public LogArchiveService logArchiveService(LoggerProperties properties) {
        return new LogArchiveService(properties.getArchive(), logDirectory(properties),
                properties.getOutput().getFileName());
    }

    @Bean
    @ConditionalOnMissingBean(LogCleanupService.class)
    @ConditionalOnProperty(prefix = "modality.logger.archive", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public LogCleanupService logCleanupService(LoggerProperties properties) {
        return new LogCleanupService(properties.getArchive(), logDirectory(properties),
                archiveDirectory(properties), properties.getOutput().getFileName());
    }

    @Bean
    @ConditionalOnMissingBean(LogMaintenanceScheduler.class)
    @ConditionalOnProperty(prefix = "modality.logger.archive", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public LogMaintenanceScheduler logMaintenanceScheduler(LoggerProperties properties,
                                                           ObjectProvider<LogArchiveService> archiveService,
                                                           ObjectProvider<LogCleanupService> cleanupService,
                                                           ObjectProvider<LogLevelManager> levelManager,
                                                           ObjectProvider<SamplingManager> samplingManager) {
        List<Runnable> refreshTasks = new ArrayList<>();
        levelManager.ifAvailable(manager -> refreshTasks.add(manager::refresh));
        samplingManager.ifAvailable(manager -> refreshTasks.add(manager::refresh));
        return new LogMaintenanceScheduler(properties.getArchive(), archiveService.getIfAvailable(),
                cleanupService.getIfAvailable(), refreshTasks);
    }

    @Bean
    @ConditionalOnMissingBean(LoggingStartupRefresher.class)
    public LoggingStartupRefresher loggingStartupRefresher(ObjectProvider<LogLevelManager> levelManager,
                                                           ObjectProvider<SamplingManager> samplingManager) {
        return new LoggingStartupRefresher(levelManager.getIfAvailable(), samplingManager.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(LogFileQueryService.class)
    @ConditionalOnProperty(prefix = "modality.logger.query", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public LogFileQueryService logFileQueryService(LoggerProperties properties) {
        return new LogFileQueryService(properties.getQuery(), logDirectory(properties),
                archiveDirectory(properties));
    }

    private Path logDirectory(LoggerProperties properties) {
        return Path.of(properties.getOutput().getDir()).toAbsolutePath().normalize();
    }

    private Path archiveDirectory(LoggerProperties properties) {
        String configured = properties.getArchive().getArchiveDir();
        return configured == null || configured.isBlank()
                ? logDirectory(properties).resolve("archive")
                : Path.of(configured).toAbsolutePath().normalize();
    }
}
