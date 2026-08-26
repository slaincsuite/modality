package cn.slainc.tenant.lifecycle;

import cn.slainc.core.exception.BizException;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.tenant.enums.TenantStatus;
import cn.slainc.tenant.isolation.TenantInfo;
import cn.slainc.tenant.isolation.TenantIsolationDispatcher;
import cn.slainc.tenant.isolation.TenantIsolationStrategy;
import cn.slainc.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantLifecycleServiceTest {

    private final TenantService tenantService = mock(TenantService.class);

    private final TenantIsolationDispatcher dispatcher = mock(TenantIsolationDispatcher.class);

    private final List<Tenant> initialized = new ArrayList<>();

    private TenantLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        TenantInitializer initializer = tenant -> initialized.add(tenant);
        lifecycleService = new TenantLifecycleService(tenantService, dispatcher, List.of(initializer),
                TenantLifecycleOptions.defaults());
        when(tenantService.create(any())).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            if (tenant.getId() == null) {
                tenant.setId(100L);
            }
            return tenant;
        });
    }

    @Test
    @DisplayName("创建租户会初始化隔离与默认数据")
    void createInitializesIsolationAndData() {
        Tenant tenant = newTenant();
        Tenant created = lifecycleService.create(tenant);

        assertEquals(100L, created.getId());
        verify(dispatcher).apply(any(TenantInfo.class));
        assertEquals(List.of(created), initialized);
    }

    @Test
    @DisplayName("隔离处理器回填的 schema 与数据源名称会写回租户")
    void writesBackIsolationMetadata() {
        doAnswer(invocation -> {
            TenantInfo info = invocation.getArgument(0);
            info.setSchemaName("tenant_acme");
            info.setDatasourceName("tenant_100");
            return null;
        }).when(dispatcher).apply(any(TenantInfo.class));
        when(tenantService.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Tenant created = lifecycleService.create(newTenant());

        assertEquals("tenant_acme", created.getSchemaName());
        assertEquals("tenant_100", created.getDatasourceName());
        verify(tenantService).update(created);
    }

    @Test
    @DisplayName("隔离初始化失败时以中文业务异常上抛")
    void wrapsIsolationFailure() {
        doThrow(new IllegalStateException("连接超时")).when(dispatcher).apply(any(TenantInfo.class));
        BizException exception = assertThrows(BizException.class, () -> lifecycleService.create(newTenant()));
        assertTrue(exception.getMessage().contains("隔离初始化失败"), exception.getMessage());

        doThrow(new BizException("schema 名称非法")).when(dispatcher).apply(any(TenantInfo.class));
        BizException bizException = assertThrows(BizException.class, () -> lifecycleService.create(newTenant()));
        assertEquals("schema 名称非法", bizException.getMessage());
    }

    @Test
    @DisplayName("试用租户带默认试用期")
    void trialSetsStatusAndExpireAt() {
        Tenant created = lifecycleService.trial(newTenant());
        assertEquals(TenantStatus.TRIAL, created.getStatus());
        assertNotNull(created.getExpireAt());
        assertTrue(created.getExpireAt().isAfter(LocalDateTime.now()));

        Tenant explicit = newTenant();
        LocalDateTime expireAt = LocalDateTime.now().plusDays(3L);
        explicit.setExpireAt(expireAt);
        assertEquals(expireAt, lifecycleService.trial(explicit).getExpireAt());
    }

    @Test
    @DisplayName("续期要求未来时间，并让试用与过期租户恢复为正常")
    void renewValidatesAndActivates() {
        assertThrows(BizException.class, () -> lifecycleService.renew(1L, null));
        assertThrows(BizException.class, () -> lifecycleService.renew(1L, LocalDateTime.now().minusDays(1L)));

        Tenant trial = newTenant();
        trial.setId(1L);
        trial.setStatus(TenantStatus.TRIAL);
        when(tenantService.requireById(1L)).thenReturn(trial);
        when(tenantService.update(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Tenant renewed = lifecycleService.renew(1L, LocalDateTime.now().plusDays(30L));
        assertEquals(TenantStatus.ACTIVE, renewed.getStatus());
    }

    @Test
    @DisplayName("冻结、过期、启用按状态机流转，停用租户受限")
    void statusTransitions() {
        Tenant tenant = newTenant();
        tenant.setId(1L);
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantService.requireById(1L)).thenReturn(tenant);
        when(tenantService.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(TenantStatus.FROZEN, lifecycleService.freeze(1L).getStatus());
        assertEquals(TenantStatus.ACTIVE, lifecycleService.activate(1L).getStatus());
        assertEquals(TenantStatus.EXPIRED, lifecycleService.expire(1L).getStatus());
        assertEquals(TenantStatus.ACTIVE, lifecycleService.activate(1L).getStatus());

        Tenant disabled = newTenant();
        disabled.setId(2L);
        disabled.setStatus(TenantStatus.DISABLED);
        when(tenantService.requireById(2L)).thenReturn(disabled);
        assertThrows(BizException.class, () -> lifecycleService.activate(2L));
        assertThrows(BizException.class, () -> lifecycleService.freeze(2L));
        assertThrows(BizException.class, () -> lifecycleService.expire(2L));
        assertThrows(BizException.class, () -> lifecycleService.renew(2L, LocalDateTime.now().plusDays(1L)));
    }

    @Test
    @DisplayName("删除默认只做标记删除，不清理隔离资源")
    void deleteOnlyMarksDeletedByDefault() {
        Tenant tenant = newTenant();
        tenant.setId(1L);
        tenant.setStatus(TenantStatus.ACTIVE);
        when(tenantService.requireById(1L)).thenReturn(tenant);

        lifecycleService.delete(1L);

        verify(tenantService).delete(1L);
        verify(dispatcher, never()).remove(any(TenantInfo.class));
    }

    @Test
    @DisplayName("开启配置后删除会连带清理隔离资源")
    void deleteRemovesIsolationWhenConfigured() {
        TenantLifecycleService withCleanup = new TenantLifecycleService(tenantService, dispatcher, List.of(),
                new TenantLifecycleOptions(30, 0, true, true));
        Tenant tenant = newTenant();
        tenant.setId(1L);
        when(tenantService.requireById(1L)).thenReturn(tenant);

        withCleanup.delete(1L);

        verify(dispatcher).remove(any(TenantInfo.class));
        verify(tenantService).delete(1L);
    }

    @Test
    @DisplayName("默认有效期只在配置了天数时补齐")
    void fillsDefaultExpireDays() {
        TenantLifecycleService withDefault = new TenantLifecycleService(tenantService, null, List.of(),
                new TenantLifecycleOptions(30, 15, false, false));
        Tenant created = withDefault.create(newTenant());
        assertNotNull(created.getExpireAt());
        assertTrue(withDefault.initializers().isEmpty());

        TenantLifecycleService withoutInitializer = new TenantLifecycleService(tenantService, null, null, null);
        assertNotNull(withoutInitializer.create(newTenant()).getId());
    }

    private static Tenant newTenant() {
        Tenant tenant = new Tenant();
        tenant.setTenantCode("acme");
        tenant.setTenantName("Acme 公司");
        tenant.setIsolationStrategy(TenantIsolationStrategy.ROW);
        return tenant;
    }
}
