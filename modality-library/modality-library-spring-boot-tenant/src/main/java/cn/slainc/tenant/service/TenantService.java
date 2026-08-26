package cn.slainc.tenant.service;

import cn.slainc.core.constant.CacheKeys;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.tenant.enums.TenantStatus;
import cn.slainc.tenant.isolation.TenantIsolationStrategy;
import cn.slainc.tenant.mapper.TenantMapper;
import cn.slainc.tenant.resolver.TenantLookup;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;

import java.util.List;

public class TenantService implements TenantLookup {

    private static final String CODE_SEGMENT = "code:";

    private final TenantMapper tenantMapper;

    private final Cache<String, Tenant> cache;

    private final TenantCacheNotifier notifier;

    public TenantService(TenantMapper tenantMapper, Cache<String, Tenant> cache, TenantCacheNotifier notifier) {
        this.tenantMapper = tenantMapper;
        this.cache = cache;
        this.notifier = notifier == null ? TenantCacheNotifier.NOOP : notifier;
    }

    public static String idKey(Long tenantId) {
        return CacheKeys.TENANT + tenantId;
    }

    public static String codeKey(String tenantCode) {
        return CacheKeys.TENANT + CODE_SEGMENT + tenantCode;
    }

    public Tenant create(Tenant tenant) {
        if (tenant == null) {
            throw new BizException("租户信息不能为空");
        }
        String code = normalizeCode(tenant.getTenantCode());
        if (code.isEmpty()) {
            throw new BizException("租户编码不能为空");
        }
        if (tenant.getTenantName() == null || tenant.getTenantName().isBlank()) {
            throw new BizException("租户名称不能为空");
        }
        if (existsByCode(code)) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "租户编码已存在：" + code);
        }
        tenant.setTenantCode(code);
        if (tenant.getStatus() == null) {
            tenant.setStatus(TenantStatus.TRIAL);
        }
        if (tenant.getIsolationStrategy() == null) {
            tenant.setIsolationStrategy(TenantIsolationStrategy.ROW);
        }
        tenantMapper.insert(tenant);
        return tenant;
    }

    public Tenant update(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) {
            throw new BizException("租户 ID 不能为空");
        }
        Tenant existing = tenantMapper.selectById(tenant.getId());
        if (existing == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "租户不存在：" + tenant.getId());
        }
        if (tenant.getTenantCode() != null && !tenant.getTenantCode().equals(existing.getTenantCode())) {
            throw new BizException("租户编码不允许修改：" + existing.getTenantCode());
        }
        tenantMapper.updateById(tenant);
        evict(tenant.getId(), existing.getTenantCode());
        if (tenant.getTenantCode() != null) {
            evict(tenant.getId(), tenant.getTenantCode());
        }
        return tenantMapper.selectById(tenant.getId());
    }

    public Tenant requireById(Long tenantId) {
        Tenant tenant = findById(tenantId);
        if (tenant == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "租户不存在：" + tenantId);
        }
        return tenant;
    }

    public Tenant findById(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        return cache.get(idKey(tenantId), key -> tenantMapper.selectById(tenantId));
    }

    public Tenant findByCode(String tenantCode) {
        String code = normalizeCode(tenantCode);
        if (code.isEmpty()) {
            return null;
        }
        return cache.get(codeKey(code), key -> tenantMapper.selectOne(
                Wrappers.<Tenant>lambdaQuery().eq(Tenant::getTenantCode, code)));
    }

    @Override
    public Long findTenantIdByCode(String tenantCode) {
        Tenant tenant = findByCode(tenantCode);
        return tenant == null ? null : tenant.getId();
    }

    public boolean existsByCode(String tenantCode) {
        return findByCode(tenantCode) != null;
    }

    public List<Tenant> list() {
        return tenantMapper.selectList(null);
    }

    public void delete(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "租户不存在：" + tenantId);
        }
        tenantMapper.deleteById(tenantId);
        evict(tenantId, tenant.getTenantCode());
    }

    public void evict(Long tenantId) {
        Tenant cached = tenantId == null ? null : cache.getIfPresent(idKey(tenantId));
        evict(tenantId, cached == null ? null : cached.getTenantCode());
    }

    public void evictAll() {
        cache.invalidateAll();
        notifier.notify(null);
    }

    private void evict(Long tenantId, String tenantCode) {
        if (tenantId != null) {
            String key = idKey(tenantId);
            cache.invalidate(key);
            notifier.notify(key);
        }
        String code = normalizeCode(tenantCode);
        if (!code.isEmpty()) {
            String key = codeKey(code);
            cache.invalidate(key);
            notifier.notify(key);
        }
    }

    private static String normalizeCode(String tenantCode) {
        return tenantCode == null ? "" : tenantCode.trim();
    }
}
