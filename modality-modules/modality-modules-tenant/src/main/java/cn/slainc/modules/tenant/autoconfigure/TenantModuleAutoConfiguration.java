package cn.slainc.modules.tenant.autoconfigure;

import cn.slainc.modules.tenant.service.TenantManageService;
import cn.slainc.modules.tenant.web.TenantLifecycleController;
import cn.slainc.modules.tenant.web.TenantManageController;
import cn.slainc.modules.tenant.web.TenantQueryController;
import cn.slainc.modules.tenant.web.TenantQuotaController;
import cn.slainc.modules.tenant.web.TenantStatsController;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.tenant.lifecycle.TenantLifecycleService;
import cn.slainc.tenant.mapper.TenantMapper;
import cn.slainc.tenant.quota.TenantQuotaService;
import cn.slainc.tenant.service.TenantService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass(name = {"cn.slainc.tenant.lifecycle.TenantLifecycleService",
        "cn.slainc.tenant.service.TenantService"})
@ConditionalOnProperty(prefix = "modality.tenant-module", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(TenantModuleProperties.class)
@Import({TenantManageController.class, TenantLifecycleController.class, TenantQueryController.class,
        TenantQuotaController.class, TenantStatsController.class})
public class TenantModuleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TenantManageService tenantManageService(TenantMapper tenantMapper,
                                                   TenantLifecycleService lifecycleService,
                                                   TenantService tenantService,
                                                   TenantQuotaService quotaService,
                                                   ObjectProvider<DynamicConfigService> configServiceProvider) {
        return new TenantManageService(tenantMapper, lifecycleService, tenantService, quotaService, configServiceProvider);
    }
}
