package cn.slainc.tenant.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.tenant.enums.TenantStatus;
import cn.slainc.tenant.mapper.TenantMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceTest {

    private final TenantMapper tenantMapper = mock(TenantMapper.class);

    private final List<String> notified = new ArrayList<>();

    private Cache<String, Tenant> cache;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().maximumSize(100L).build();
        tenantService = new TenantService(tenantMapper, cache, notified::add);
    }

    @Test
    @DisplayName("创建租户校验编码与名称，补齐默认状态与隔离策略")
    void createValidatesAndFillsDefaults() {
        Tenant tenant = new Tenant();
        assertThrows(BizException.class, () -> tenantService.create(null));
        assertThrows(BizException.class, () -> tenantService.create(tenant));
        tenant.setTenantCode("acme");
        assertThrows(BizException.class, () -> tenantService.create(tenant));
        tenant.setTenantName("Acme 公司");

        when(tenantMapper.selectOne(any())).thenReturn(null);
        Tenant created = tenantService.create(tenant);

        assertEquals("acme", created.getTenantCode());
        assertEquals(TenantStatus.TRIAL, created.getStatus());
        assertEquals(cn.slainc.tenant.isolation.TenantIsolationStrategy.ROW, created.getIsolationStrategy());
        verify(tenantMapper).insert(tenant);
    }

    @Test
    @DisplayName("租户编码重复时拒绝创建")
    void rejectsDuplicateCode() {
        when(tenantMapper.selectOne(any())).thenReturn(existing(1L, "acme"));
        Tenant tenant = new Tenant();
        tenant.setTenantCode("acme");
        tenant.setTenantName("Acme 公司");
        BizException exception = assertThrows(BizException.class, () -> tenantService.create(tenant));
        assertTrue(exception.getMessage().contains("租户编码已存在"), exception.getMessage());
        verify(tenantMapper, never()).insert(any(cn.slainc.tenant.entity.Tenant.class));
    }

    @Test
    @DisplayName("按 ID 与编码查询命中 Caffeine 缓存")
    void cachesByIdAndCode() {
        Tenant tenant = existing(1L, "acme");
        when(tenantMapper.selectById(1L)).thenReturn(tenant);
        when(tenantMapper.selectOne(any())).thenReturn(tenant);

        assertEquals(tenant, tenantService.findById(1L));
        assertEquals(tenant, tenantService.findById(1L));
        verify(tenantMapper, times(1)).selectById(1L);

        assertEquals(tenant, tenantService.findByCode("acme"));
        assertEquals(tenant, tenantService.findByCode(" acme "));
        verify(tenantMapper, times(1)).selectOne(any());

        assertEquals(1L, tenantService.findTenantIdByCode("acme"));
        assertTrue(tenantService.existsByCode("acme"));
        assertNull(tenantService.findById(null));
        assertNull(tenantService.findByCode("  "));
        assertNull(tenantService.findTenantIdByCode(null));
    }

    @Test
    @DisplayName("查询不到租户时 requireById 抛出中文异常")
    void requireByIdFailsWhenMissing() {
        when(tenantMapper.selectById(9L)).thenReturn(null);
        BizException exception = assertThrows(BizException.class, () -> tenantService.requireById(9L));
        assertTrue(exception.getMessage().contains("租户不存在"), exception.getMessage());
    }

    @Test
    @DisplayName("更新租户校验存在性与编码不可变，并失效缓存")
    void updateValidatesAndEvictsCache() {
        Tenant stored = existing(1L, "acme");
        when(tenantMapper.selectById(1L)).thenReturn(stored);
        when(tenantMapper.selectOne(any())).thenReturn(stored);
        assertEquals(stored, tenantService.findById(1L));

        assertThrows(BizException.class, () -> tenantService.update(new Tenant()));
        assertThrows(BizException.class, () -> tenantService.update(new Tenant()));

        Tenant changed = new Tenant();
        changed.setId(1L);
        changed.setTenantCode("other");
        assertThrows(BizException.class, () -> tenantService.update(changed));

        Tenant updated = new Tenant();
        updated.setId(1L);
        updated.setTenantCode("acme");
        updated.setTenantName("Acme 新名称");
        tenantService.update(updated);
        verify(tenantMapper).updateById(updated);
        assertEquals(stored, tenantService.findById(1L));
        verify(tenantMapper, org.mockito.Mockito.atLeast(2)).selectById(1L);
        assertTrue(notified.contains(TenantService.idKey(1L)));
        assertTrue(notified.contains(TenantService.codeKey("acme")));
    }

    @Test
    @DisplayName("删除租户为标记删除并失效缓存")
    void deleteEvictsCache() {
        Tenant tenant = existing(1L, "acme");
        when(tenantMapper.selectById(1L)).thenReturn(tenant);
        when(tenantMapper.selectOne(any())).thenReturn(tenant);
        tenantService.findById(1L);
        tenantService.findByCode("acme");

        tenantService.delete(1L);

        verify(tenantMapper).deleteById(1L);
        assertNull(cache.getIfPresent(TenantService.idKey(1L)));
        assertNull(cache.getIfPresent(TenantService.codeKey("acme")));

        when(tenantMapper.selectById(8L)).thenReturn(null);
        assertThrows(BizException.class, () -> tenantService.delete(8L));
    }

    @Test
    @DisplayName("清空缓存并通知外部失效")
    void evictAllClearsCache() {
        when(tenantMapper.selectById(1L)).thenReturn(existing(1L, "acme"));
        tenantService.findById(1L);
        notified.clear();
        tenantService.evictAll();
        assertNull(cache.getIfPresent(TenantService.idKey(1L)));
        assertEquals(List.of(), notified.stream().filter(java.util.Objects::nonNull).toList());
        assertEquals(1, notified.size());
    }

    @Test
    @DisplayName("list 直接透传 Mapper 查询")
    void listDelegatesToMapper() {
        when(tenantMapper.selectList(null)).thenReturn(List.of(existing(1L, "acme")));
        assertEquals(1, tenantService.list().size());
        assertFalse(tenantService.list().isEmpty());
    }

    private static Tenant existing(Long id, String code) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setTenantCode(code);
        tenant.setTenantName(code + " 公司");
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenant;
    }
}
