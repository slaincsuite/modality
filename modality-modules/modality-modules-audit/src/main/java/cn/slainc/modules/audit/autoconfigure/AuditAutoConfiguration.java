package cn.slainc.modules.audit.autoconfigure;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.modules.audit.alert.AuditAlertChannel;
import cn.slainc.modules.audit.service.AuditAlertService;
import cn.slainc.modules.audit.excel.ApiLogExportService;
import cn.slainc.modules.audit.service.ApiLogService;
import cn.slainc.modules.audit.service.AuditCleanupService;
import cn.slainc.modules.audit.config.AuditConfigReader;
import cn.slainc.modules.audit.excel.AuditLogExportService;
import cn.slainc.modules.audit.excel.OnlineUserExcelService;
import cn.slainc.modules.audit.listener.AuditEventCollector;
import cn.slainc.modules.audit.service.AuditLogService;
import cn.slainc.modules.audit.store.JdbcAuditLogStore;
import cn.slainc.modules.audit.mapper.ApiLogMapper;
import cn.slainc.modules.audit.mapper.AuditLogMapper;
import cn.slainc.modules.audit.mapper.PolicyEvaluationLogMapper;
import cn.slainc.modules.audit.service.PolicyEvaluationLogService;
import cn.slainc.modules.audit.web.ApiLogController;
import cn.slainc.modules.audit.web.AuditLogController;
import cn.slainc.modules.audit.web.AuditMaintenanceController;
import cn.slainc.modules.audit.web.OnlineUserAdminController;
import cn.slainc.modules.audit.web.PolicyEvaluationLogController;
import cn.slainc.security.login.CurrentRequestReader;
import cn.slainc.session.kick.KickSessionService;
import cn.slainc.session.online.OnlineUserService;
import cn.slainc.session.stats.SessionStatsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@AutoConfiguration(afterName = "cn.slainc.session.autoconfigure.SessionKickAutoConfiguration")
@ConditionalOnProperty(prefix = "modality.audit", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(AuditProperties.class)
@Import({AuditLogController.class, ApiLogController.class, PolicyEvaluationLogController.class,
        AuditMaintenanceController.class})
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditConfigReader auditConfigReader(AuditProperties properties,
                                               ObjectProvider<DynamicConfigService> dynamicConfig) {
        return new AuditConfigReader(properties, dynamicConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogService auditLogService(AuditLogMapper auditLogMapper) {
        return new AuditLogService(auditLogMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiLogService apiLogService(ApiLogMapper apiLogMapper) {
        return new ApiLogService(apiLogMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public PolicyEvaluationLogService policyEvaluationLogService(PolicyEvaluationLogMapper mapper) {
        return new PolicyEvaluationLogService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(AuditEventCollector.class)
    public AuditEventCollector auditEventCollector(AuditLogService auditLogService,
                                                   ObjectProvider<CurrentRequestReader> requestReader) {
        return new AuditEventCollector(auditLogService, requestReader.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(JdbcAuditLogStore.class)
    public JdbcAuditLogStore jdbcAuditLogStore(AuditLogService auditLogService) {
        return new JdbcAuditLogStore(auditLogService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditLogExportService auditLogExportService(AuditLogService auditLogService) {
        return new AuditLogExportService(auditLogService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApiLogExportService apiLogExportService(ApiLogService apiLogService) {
        return new ApiLogExportService(apiLogService);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditAlertService auditAlertService(AuditLogService auditLogService, AuditConfigReader configReader,
                                               ObjectProvider<AuditAlertChannel> channels) {
        return new AuditAlertService(auditLogService, configReader, channels);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditCleanupService auditCleanupService(AuditLogService auditLogService, ApiLogService apiLogService,
                                                   ObjectProvider<PolicyEvaluationLogService> policyLogService,
                                                   AuditConfigReader configReader) {
        return new AuditCleanupService(auditLogService, apiLogService, policyLogService, configReader);
    }

    // 在线用户管理依赖 session 库的查询与踢人服务：无 Redis（KickService 不装配）或宿主关闭 session 时整组端点退避
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean({OnlineUserService.class, KickSessionService.class, SessionStatsService.class})
    @Import(OnlineUserAdminController.class)
    static class OnlineConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OnlineUserExcelService onlineUserExcelService() {
            return new OnlineUserExcelService();
        }
    }
}
