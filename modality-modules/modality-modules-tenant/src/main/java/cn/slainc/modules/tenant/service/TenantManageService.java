package cn.slainc.modules.tenant.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.tenant.web.TenantCreateRequest;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.tenant.enums.TenantStatus;
import cn.slainc.tenant.isolation.TenantIsolationStrategy;
import cn.slainc.tenant.lifecycle.TenantLifecycleService;
import cn.slainc.tenant.mapper.TenantMapper;
import cn.slainc.tenant.quota.TenantQuota;
import cn.slainc.tenant.quota.TenantQuotaService;
import cn.slainc.tenant.service.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;
import java.util.List;

public class TenantManageService {

    private final TenantMapper tenantMapper;

    private final TenantLifecycleService lifecycleService;

    private final TenantService tenantService;

    private final TenantQuotaService quotaService;

    public TenantManageService(TenantMapper tenantMapper, TenantLifecycleService lifecycleService,
                               TenantService tenantService, TenantQuotaService quotaService) {
        this.tenantMapper = tenantMapper;
        this.lifecycleService = lifecycleService;
        this.tenantService = tenantService;
        this.quotaService = quotaService;
    }

    public PageResult<Tenant> page(String status, String keyword, long current, long size) {
        TenantStatus statusEnum = TenantStatus.of(status, null);
        String trimmed = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<Tenant>()
                .eq(statusEnum != null, Tenant::getStatus, statusEnum)
                .and(!trimmed.isEmpty(), w -> w.like(Tenant::getTenantCode, trimmed).or().like(Tenant::getTenantName, trimmed))
                .orderByDesc(Tenant::getId);
        Page<Tenant> page = tenantMapper.selectPage(Page.of(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<Tenant> list() {
        return tenantService.list();
    }

    public Tenant require(Long tenantId) {
        return tenantService.requireById(tenantId);
    }

    public TenantQuota quota(Long tenantId) {
        require(tenantId);
        return quotaService.quotaOf(tenantId);
    }

    public Tenant create(TenantCreateRequest request) {
        return lifecycleService.create(toTenant(request));
    }

    public Tenant trial(TenantCreateRequest request) {
        return lifecycleService.trial(toTenant(request));
    }

    public Tenant renew(Long tenantId, LocalDateTime expireAt) {
        return lifecycleService.renew(tenantId, expireAt);
    }

    public Tenant freeze(Long tenantId) {
        return lifecycleService.freeze(tenantId);
    }

    public Tenant activate(Long tenantId) {
        return lifecycleService.activate(tenantId);
    }

    public Tenant expire(Long tenantId) {
        return lifecycleService.expire(tenantId);
    }

    public void delete(Long tenantId) {
        if (tenantId != null && tenantId == 1L) {
            throw new BizException("平台租户不允许删除");
        }
        lifecycleService.delete(tenantId);
    }

    private Tenant toTenant(TenantCreateRequest request) {
        Tenant tenant = new Tenant();
        tenant.setTenantCode(request.tenantCode());
        tenant.setTenantName(request.tenantName());
        tenant.setContactName(request.contactName());
        tenant.setContactPhone(request.contactPhone());
        tenant.setContactEmail(request.contactEmail());
        tenant.setExpireAt(request.expireAt());
        tenant.setIsolationStrategy(parseStrategy(request.isolationStrategy()));
        tenant.setRemark(request.remark());
        return tenant;
    }

    private TenantIsolationStrategy parseStrategy(String text) {
        if (text == null || text.isBlank()) {
            return TenantIsolationStrategy.ROW;
        }
        for (TenantIsolationStrategy strategy : TenantIsolationStrategy.values()) {
            if (strategy.name().equalsIgnoreCase(text.trim())) {
                return strategy;
            }
        }
        throw new BizException("非法的隔离策略: " + text + "，只允许 ROW、SCHEMA、DATABASE");
    }
}
