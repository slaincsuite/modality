package cn.slainc.modules.tenant.autoconfigure;

import cn.slainc.modules.tenant.mapper.TenantPlanMapper;
import cn.slainc.modules.tenant.mapper.TenantPlanMenuMapper;
import cn.slainc.modules.tenant.plan.TenantPlanAdapter;
import cn.slainc.modules.tenant.service.manage.TenantManageService;
import cn.slainc.modules.tenant.service.plan.TenantPlanService;
import cn.slainc.modules.tenant.web.lifecycle.TenantLifecycleController;
import cn.slainc.modules.tenant.web.manage.TenantManageController;
import cn.slainc.modules.tenant.web.plan.TenantPlanBindController;
import cn.slainc.modules.tenant.web.plan.TenantPlanController;
import cn.slainc.modules.tenant.web.query.TenantQueryController;
import cn.slainc.modules.tenant.web.quota.TenantQuotaController;
import cn.slainc.modules.tenant.web.stats.TenantStatsController;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.tenant.lifecycle.TenantLifecycleService;
import cn.slainc.tenant.dao.TenantMapper;
import cn.slainc.tenant.quota.TenantQuotaService;
import cn.slainc.tenant.lookup.TenantService;
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
        "cn.slainc.tenant.lookup.TenantService"})
@ConditionalOnProperty(prefix = "modality.tenant-module", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(TenantModuleProperties.class)
@Import({TenantManageController.class, TenantLifecycleController.class, TenantQueryController.class,
        TenantQuotaController.class, TenantStatsController.class, TenantPlanController.class,
        TenantPlanBindController.class})
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

    @Bean
    @ConditionalOnMissingBean(TenantPlanAdapter.class)
    public TenantPlanAdapter tenantPlanAdapter(ObjectProvider<TenantMapper> tenantMapperProvider,
                                               ObjectProvider<TenantPlanMapper> planMapperProvider,
                                               ObjectProvider<TenantPlanMenuMapper> planMenuMapperProvider,
                                               ObjectProvider<DynamicConfigService> configServiceProvider,
                                               TenantModuleProperties properties) {
        return new TenantPlanAdapter(tenantMapperProvider, planMapperProvider, planMenuMapperProvider,
                configServiceProvider, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public TenantPlanService tenantPlanService(TenantPlanMapper planMapper, TenantPlanMenuMapper planMenuMapper,
                                               ObjectProvider<TenantMapper> tenantMapperProvider,
                                               ObjectProvider<TenantService> tenantServiceProvider,
                                               ObjectProvider<TenantPlanAdapter> adapterProvider) {
        return new TenantPlanService(planMapper, planMenuMapper, tenantMapperProvider, tenantServiceProvider,
                adapterProvider);
    }
}
