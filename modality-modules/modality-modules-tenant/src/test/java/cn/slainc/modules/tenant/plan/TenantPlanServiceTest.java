package cn.slainc.modules.tenant.plan;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.tenant.dto.TenantPlanForm;
import cn.slainc.modules.tenant.entity.TenantPlanMenuRecord;
import cn.slainc.modules.tenant.entity.TenantPlanRecord;
import cn.slainc.modules.tenant.mapper.TenantPlanMapper;
import cn.slainc.modules.tenant.mapper.TenantPlanMenuMapper;
import cn.slainc.modules.tenant.service.TenantPlanService;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.tenant.mapper.TenantMapper;
import cn.slainc.tenant.service.TenantService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantPlanServiceTest {

    private final TenantPlanMapper planMapper = mock(TenantPlanMapper.class);

    private final TenantPlanMenuMapper planMenuMapper = mock(TenantPlanMenuMapper.class);

    private final TenantMapper tenantMapper = mock(TenantMapper.class);

    private final TenantService tenantService = mock(TenantService.class);

    private final TenantPlanAdapter adapter = mock(TenantPlanAdapter.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TenantMapper> tenantMapperProvider = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TenantService> tenantServiceProvider = mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<TenantPlanAdapter> adapterProvider = mock(ObjectProvider.class);

    private final TenantPlanService service;

    TenantPlanServiceTest() {
        when(tenantMapperProvider.getIfAvailable()).thenReturn(tenantMapper);
        when(tenantServiceProvider.getIfAvailable()).thenReturn(tenantService);
        when(adapterProvider.getIfAvailable()).thenReturn(adapter);
        when(adapter.load(any())).thenReturn(limits());
        service = new TenantPlanService(planMapper, planMenuMapper, tenantMapperProvider, tenantServiceProvider,
                adapterProvider);
    }

    @BeforeAll
    static void initializeLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TenantPlanRecord.class);
        TableInfoHelper.initTableInfo(assistant, TenantPlanMenuRecord.class);
        TableInfoHelper.initTableInfo(assistant, Tenant.class);
    }

    @Test
    void shouldRejectADuplicatePlanCode() {
        when(planMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(form()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("套餐编码已存在：standard");
        verify(planMapper, never()).insert(any(TenantPlanRecord.class));
    }

    @Test
    void shouldStoreAPlanWithTheDefaults() {
        when(planMapper.selectCount(any())).thenReturn(0L);
        TenantPlanRecord[] inserted = new TenantPlanRecord[1];
        when(planMapper.insert(any(TenantPlanRecord.class))).thenAnswer(invocation -> {
            TenantPlanRecord record = invocation.getArgument(0);
            record.setId(9L);
            inserted[0] = record;
            return 1;
        });
        when(planMapper.selectById(9L)).thenAnswer(invocation -> inserted[0]);

        service.create(form());

        assertThat(inserted[0].getEnabled()).isTrue();
        assertThat(inserted[0].getSort()).isZero();
        assertThat(inserted[0].getCreateTime()).isNotNull();
        assertThat(inserted[0].getPlanCode()).isEqualTo("standard");
        verify(adapter).evictAll();
    }

    @Test
    void shouldReplaceTheMenuScope() {
        when(planMapper.selectById(9L)).thenReturn(record(9L));

        assertThat(service.assignMenus(9L, List.of(100L, 100L, 112L))).containsExactly(100L, 112L);

        verify(planMenuMapper).delete(any());
        verify(planMenuMapper, times(2)).insert(any(TenantPlanMenuRecord.class));
        verify(adapter).evictAll();
    }

    @Test
    void shouldBindThePlanWithoutTouchingMenuAuthorization() {
        when(tenantService.requireById(1L)).thenReturn(tenant(1L));
        when(tenantMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(planMapper.selectById(9L)).thenReturn(record(9L));

        TenantPlanLimits limits = service.bind(1L, 9L);

        assertThat(limits.planId()).isEqualTo(9L);
        ArgumentCaptor<LambdaUpdateWrapper<Tenant>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(tenantMapper).update(any(), captor.capture());
        assertThat(captor.getValue().getParamNameValuePairs().containsValue(9L)).isTrue();
        verify(tenantService).evict(1L);
        verify(adapter).evict(1L);
        // modality 用户菜单为平台级表，绑定套餐不写任何租户菜单授权
        verify(planMenuMapper, never()).selectList(any());
    }

    @Test
    void shouldUnbindWithoutTouchingTheMenuScope() {
        when(tenantService.requireById(1L)).thenReturn(tenant(1L));
        when(tenantMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.bind(1L, null);

        ArgumentCaptor<LambdaUpdateWrapper<Tenant>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(tenantMapper).update(any(), captor.capture());
        // 解绑显式把 package_id 置空（updateById 会跳过 null 字段）
        assertThat(captor.getValue().getSqlSet()).contains("package_id");
        assertThat(captor.getValue().getParamNameValuePairs().containsValue(null)).isTrue();
        verify(planMenuMapper, never()).selectList(any());
        verify(adapter).evict(1L);
    }

    @Test
    void shouldRefuseBindingADisabledPlan() {
        when(tenantService.requireById(1L)).thenReturn(tenant(1L));
        TenantPlanRecord disabled = record(9L);
        disabled.setEnabled(Boolean.FALSE);
        when(planMapper.selectById(9L)).thenReturn(disabled);

        assertThatThrownBy(() -> service.bind(1L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessage("套餐已停用，不能绑定");
    }

    @Test
    void shouldRefuseAnUnknownTenantOrPlan() {
        when(tenantService.requireById(2L)).thenThrow(new BizException("租户不存在：2"));
        assertThatThrownBy(() -> service.effective(2L))
                .isInstanceOf(BizException.class)
                .hasMessage("租户不存在：2");

        when(tenantService.requireById(1L)).thenReturn(tenant(1L));
        when(planMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.bind(1L, 404L))
                .isInstanceOf(BizException.class)
                .hasMessage("套餐不存在");
    }

    @Test
    void shouldRefuseABlankPlanCodeOrName() {
        assertThatThrownBy(() -> service.create(blankForm("  ", "标准版")))
                .isInstanceOf(BizException.class)
                .hasMessage("套餐编码不能为空");

        assertThatThrownBy(() -> service.create(blankForm("standard", " ")))
                .isInstanceOf(BizException.class)
                .hasMessage("套餐名称不能为空");
    }

    private static TenantPlanRecord record(Long id) {
        TenantPlanRecord record = new TenantPlanRecord();
        record.setId(id);
        record.setPlanCode("standard");
        record.setPlanName("标准版");
        return record;
    }

    private static TenantPlanForm form() {
        return blankForm("standard", "标准版");
    }

    private static TenantPlanForm blankForm(String planCode, String planName) {
        return new TenantPlanForm(planCode, planName, 10, 1024L, 10L, 2000, 30,
                Set.of(TenantPlanFeature.SEARCH), 300, 2, 50, TenantPlanDataScope.SELF, 0, null, null);
    }

    private static Tenant tenant(Long id) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setTenantCode("default");
        tenant.setTenantName("默认租户");
        return tenant;
    }

    private static TenantPlanLimits limits() {
        return new TenantPlanLimits(1L, 9L, "standard", 10, 1024L, 10L, 2000, 30, null, 300, 2, 50, null, List.of());
    }
}
