package cn.slainc.modules.tenant.service.manage;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.spi.UserUsageProvider;
import cn.slainc.modules.tenant.dto.TenantUsageView;
import cn.slainc.tenant.dao.TenantMapper;
import cn.slainc.tenant.domain.Tenant;
import cn.slainc.tenant.lifecycle.TenantLifecycleService;
import cn.slainc.tenant.lookup.TenantService;
import cn.slainc.tenant.quota.TenantQuota;
import cn.slainc.tenant.quota.TenantQuotaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantManageServiceTest {

    private final TenantMapper tenantMapper = mock(TenantMapper.class);

    private final TenantLifecycleService lifecycleService = mock(TenantLifecycleService.class);

    private final TenantService tenantService = mock(TenantService.class);

    private final TenantQuotaService quotaService = mock(TenantQuotaService.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<DynamicConfigService> configServiceProvider = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<UserUsageProvider> userUsageProvider = mock(ObjectProvider.class);

    @Test
    void returnsNullUserUsageWhenProviderIsMissing() {
        when(tenantService.requireById(1L)).thenReturn(new Tenant());
        when(quotaService.quotaOf(1L)).thenReturn(TenantQuota.of(20L, 30L, 40L, 50L));
        when(userUsageProvider.getIfAvailable()).thenReturn(null);
        TenantManageService service = new TenantManageService(tenantMapper, lifecycleService, tenantService,
                quotaService, configServiceProvider, userUsageProvider);

        TenantUsageView usage = service.usage(1L);

        assertThat(usage.maxUsers()).isEqualTo(20L);
        assertThat(usage.userCount()).isNull();
        assertThat(usage.activeUserCount()).isNull();
        assertThat(usage.disabledUserCount()).isNull();
    }

    @Test
    void returnsNullUserUsageWhenProviderFails() {
        when(tenantService.requireById(1L)).thenReturn(new Tenant());
        when(quotaService.quotaOf(1L)).thenReturn(TenantQuota.of(20L, 30L, 40L, 50L));
        UserUsageProvider provider = mock(UserUsageProvider.class);
        when(provider.usage(1L)).thenThrow(new IllegalStateException("用户库不可用"));
        when(userUsageProvider.getIfAvailable()).thenReturn(provider);
        TenantManageService service = new TenantManageService(tenantMapper, lifecycleService, tenantService,
                quotaService, configServiceProvider, userUsageProvider);

        TenantUsageView usage = service.usage(1L);

        assertThat(usage.userCount()).isNull();
    }
}
