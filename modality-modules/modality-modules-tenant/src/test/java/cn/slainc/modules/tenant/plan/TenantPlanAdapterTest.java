package cn.slainc.modules.tenant.plan;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.modules.tenant.autoconfigure.TenantModuleProperties;
import cn.slainc.modules.tenant.entity.TenantPlanMenuRecord;
import cn.slainc.modules.tenant.entity.TenantPlanRecord;
import cn.slainc.modules.tenant.mapper.TenantPlanMapper;
import cn.slainc.modules.tenant.mapper.TenantPlanMenuMapper;
import cn.slainc.tenant.domain.Tenant;
import cn.slainc.tenant.dao.TenantMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantPlanAdapterTest {

    private final TenantMapper tenantMapper = mock(TenantMapper.class);

    private final TenantPlanMapper planMapper = mock(TenantPlanMapper.class);

    private final TenantPlanMenuMapper planMenuMapper = mock(TenantPlanMenuMapper.class);

    private final DynamicConfigService configService = mock(DynamicConfigService.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TenantMapper> tenantMapperProvider = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TenantPlanMapper> planMapperProvider = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TenantPlanMenuMapper> planMenuMapperProvider = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<DynamicConfigService> configServiceProvider = mock(ObjectProvider.class);

    private final TenantPlanAdapter adapter = adapter(true);

    TenantPlanAdapterTest() {
        when(configServiceProvider.getIfAvailable()).thenReturn(configService);
        when(configService.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        when(configService.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
    }

    @BeforeAll
    static void initializeLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TenantPlanRecord.class);
        TableInfoHelper.initTableInfo(assistant, TenantPlanMenuRecord.class);
        TableInfoHelper.initTableInfo(assistant, Tenant.class);
    }

    private TenantPlanAdapter adapter(boolean withStores) {
        if (withStores) {
            when(tenantMapperProvider.getIfAvailable()).thenReturn(tenantMapper);
            when(planMapperProvider.getIfAvailable()).thenReturn(planMapper);
            when(planMenuMapperProvider.getIfAvailable()).thenReturn(planMenuMapper);
        }
        return new TenantPlanAdapter(tenantMapperProvider, planMapperProvider, planMenuMapperProvider,
                configServiceProvider, new TenantModuleProperties());
    }

    @Test
    void shouldFallBackToGlobalDefaultsWithoutAPlan() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));

        TenantPlanLimits limits = adapter.load(1L);

        assertThat(limits.planId()).isNull();
        assertThat(limits.maxUsers()).isNull();
        assertThat(limits.maxFileSizeMb()).isEqualTo(100L);
        assertThat(limits.logRetentionDays()).isEqualTo(90);
        assertThat(limits.rateLimitPerMinute()).isEqualTo(100);
        assertThat(limits.maxConcurrentExports()).isEqualTo(5);
        assertThat(limits.searchMaxPageSize()).isEqualTo(100);
        assertThat(limits.dataScope()).isNull();
        assertThat(limits.allows(TenantPlanFeature.SEARCH)).isTrue();
    }

    @Test
    void shouldPreferTheConfiguredPlanValues() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, 9L));
        when(planMapper.selectById(9L)).thenReturn(plan(9L));

        TenantPlanLimits limits = adapter.load(1L);

        assertThat(limits.planId()).isEqualTo(9L);
        assertThat(limits.planCode()).isEqualTo("standard");
        assertThat(limits.maxUsers()).isEqualTo(10);
        assertThat(limits.maxExportRows()).isEqualTo(2000);
        assertThat(limits.logRetentionDays()).isEqualTo(30);
        assertThat(limits.dataScope()).isEqualTo(TenantPlanDataScope.SELF);
        assertThat(limits.allows(TenantPlanFeature.SEARCH)).isTrue();
        assertThat(limits.allows(TenantPlanFeature.MFA)).isFalse();
    }

    @Test
    void shouldTreatADisabledPlanAsUnbound() {
        TenantPlanRecord plan = plan(9L);
        plan.setEnabled(Boolean.FALSE);
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, 9L));
        when(planMapper.selectById(9L)).thenReturn(plan);

        assertThat(adapter.load(1L).planId()).isNull();
    }

    @Test
    void shouldCacheUntilEvicted() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));

        adapter.load(1L);
        adapter.load(1L);
        verify(tenantMapper, times(1)).selectById(1L);

        adapter.evict(1L);
        adapter.load(1L);
        verify(tenantMapper, times(2)).selectById(1L);
    }

    @Test
    void shouldReturnDefaultsForAPlatformTenant() {
        TenantPlanLimits limits = adapter.load(null);

        assertThat(limits.tenantId()).isNull();
        assertThat(limits.planId()).isNull();
        assertThat(limits.menuIds()).isEmpty();
    }

    @Test
    void shouldExposeThePlanMenuScope() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, 9L));
        when(planMapper.selectById(9L)).thenReturn(plan(9L));
        when(planMenuMapper.selectList(any())).thenReturn(List.of(planMenu(9L, 100L), planMenu(9L, 112L)));

        TenantPlanLimits limits = adapter.load(1L);

        assertThat(limits.restrictsMenus()).isTrue();
        assertThat(limits.menuIds()).containsExactly(100L, 112L);
    }

    @Test
    void shouldLetDynamicConfigOverrideTheStaticDefaults() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));
        when(configService.getInt(anyString(), anyInt())).thenAnswer(inv ->
                "modality.tenant-module.plan.max-concurrent-exports".equals(inv.getArgument(0))
                        ? 9
                        : inv.getArgument(1));
        try {
            assertThat(adapter.load(1L).maxConcurrentExports()).isEqualTo(9);
        } finally {
            resetIntDefaults();
        }
    }

    @Test
    void shouldFallBackToStaticDefaultsWhenDynamicConfigFails() {
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));
        doThrow(new RuntimeException("配置中心不可用")).when(configService).getInt(anyString(), anyInt());
        try {
            assertThat(adapter.load(1L).maxConcurrentExports()).isEqualTo(5);
            assertThat(adapter.load(1L).logRetentionDays()).isEqualTo(90);
        } finally {
            resetIntDefaults();
        }
    }

    @Test
    void shouldFallBackWhenNoConfigServiceIsAvailable() {
        TenantPlanAdapter bare = new TenantPlanAdapter(tenantMapperProvider, planMapperProvider,
                planMenuMapperProvider, configServiceProvider, null);
        when(configServiceProvider.getIfAvailable()).thenReturn(null);
        when(tenantMapperProvider.getIfAvailable()).thenReturn(tenantMapper);
        when(planMapperProvider.getIfAvailable()).thenReturn(null);
        when(tenantMapper.selectById(1L)).thenReturn(tenant(1L, null));
        try {
            assertThat(bare.load(1L).maxFileSizeMb()).isEqualTo(100L);
            assertThat(bare.load(1L).rateLimitPerMinute()).isEqualTo(100);
        } finally {
            when(configServiceProvider.getIfAvailable()).thenReturn(configService);
            when(planMapperProvider.getIfAvailable()).thenReturn(planMapper);
        }
    }

    private void resetIntDefaults() {
        doAnswer(inv -> inv.getArgument(1)).when(configService).getInt(anyString(), anyInt());
    }

    private static Tenant tenant(Long id, Long planId) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setPackageId(planId);
        return tenant;
    }

    private static TenantPlanRecord plan(Long id) {
        TenantPlanRecord plan = new TenantPlanRecord();
        plan.setId(id);
        plan.setPlanCode("standard");
        plan.setPlanName("标准版");
        plan.setMaxUsers(10);
        plan.setMaxExportRows(2000);
        plan.setLogRetentionDays(30);
        plan.dataScope(TenantPlanDataScope.SELF);
        plan.features(Set.of(TenantPlanFeature.SEARCH));
        plan.setEnabled(Boolean.TRUE);
        return plan;
    }

    private static TenantPlanMenuRecord planMenu(Long planId, Long menuId) {
        TenantPlanMenuRecord link = new TenantPlanMenuRecord();
        link.setPlanId(planId);
        link.setMenuId(menuId);
        return link;
    }
}
