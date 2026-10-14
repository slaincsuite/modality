package cn.slainc.modules.tenant.service.manage;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.excel.ExcelUtils;
import cn.slainc.modules.tenant.dto.TenantCreateRequest;
import cn.slainc.modules.tenant.dto.TenantQuotaRequest;
import cn.slainc.modules.tenant.dto.TenantUpdateRequest;
import cn.slainc.modules.tenant.excel.TenantExportRow;
import cn.slainc.tenant.domain.Tenant;
import cn.slainc.tenant.enums.TenantStatus;
import cn.slainc.tenant.isolation.TenantIsolationStrategy;
import cn.slainc.tenant.lifecycle.TenantLifecycleService;
import cn.slainc.tenant.dao.TenantMapper;
import cn.slainc.tenant.quota.TenantQuota;
import cn.slainc.tenant.quota.TenantQuotaDimension;
import cn.slainc.tenant.quota.TenantQuotaService;
import cn.slainc.tenant.lookup.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.ObjectProvider;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TenantManageService {

    private final TenantMapper tenantMapper;

    private final TenantLifecycleService lifecycleService;

    private final TenantService tenantService;

    private final TenantQuotaService quotaService;

    private final ObjectProvider<DynamicConfigService> configServiceProvider;

    public TenantManageService(TenantMapper tenantMapper, TenantLifecycleService lifecycleService,
                               TenantService tenantService, TenantQuotaService quotaService,
                               ObjectProvider<DynamicConfigService> configServiceProvider) {
        this.tenantMapper = tenantMapper;
        this.lifecycleService = lifecycleService;
        this.tenantService = tenantService;
        this.quotaService = quotaService;
        this.configServiceProvider = configServiceProvider;
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

    public Tenant requireByCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new BizException("租户编码不能为空");
        }
        Tenant tenant = tenantService.findByCode(tenantCode.trim());
        if (tenant == null) {
            throw new BizException("租户不存在：" + tenantCode);
        }
        return tenant;
    }

    public boolean codeExists(String tenantCode) {
        return tenantCode != null && !tenantCode.isBlank() && tenantService.existsByCode(tenantCode.trim());
    }

    public List<Tenant> listForExport(String status, String keyword) {
        TenantStatus statusEnum = TenantStatus.of(status, null);
        String trimmed = keyword == null ? "" : keyword.trim();
        return tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                .eq(statusEnum != null, Tenant::getStatus, statusEnum)
                .and(!trimmed.isEmpty(),
                        w -> w.like(Tenant::getTenantCode, trimmed).or().like(Tenant::getTenantName, trimmed))
                .orderByDesc(Tenant::getId));
    }

    public void exportExcel(OutputStream outputStream, String status, String keyword) {
        List<TenantExportRow> rows = new ArrayList<>();
        for (Tenant tenant : listForExport(status, keyword)) {
            rows.add(new TenantExportRow(tenant));
        }
        ExcelUtils.write(outputStream, TenantExportRow.class, rows, "租户");
    }

    public TenantQuota quota(Long tenantId) {
        require(tenantId);
        return quotaService.quotaOf(tenantId);
    }

    public Tenant create(TenantCreateRequest request) {
        return lifecycleService.create(toTenant(request));
    }

    public Tenant update(Long tenantId, TenantUpdateRequest request) {
        Tenant tenant = tenantService.requireById(tenantId);
        Tenant patch = new Tenant();
        patch.setId(tenant.getId());
        patch.setTenantName(request.tenantName());
        patch.setContactName(request.contactName());
        patch.setContactPhone(request.contactPhone());
        patch.setContactEmail(request.contactEmail());
        patch.setRemark(request.remark());
        return tenantService.update(patch);
    }

    public Tenant disable(Long tenantId) {
        Tenant tenant = tenantService.requireById(tenantId);
        if (tenant.getStatus() == TenantStatus.DISABLED) {
            return tenant;
        }
        tenant.setStatus(TenantStatus.DISABLED);
        return tenantService.update(tenant);
    }

    public TenantQuota adjustQuota(Long tenantId, TenantQuotaRequest request) {
        require(tenantId);
        DynamicConfigService configService = configServiceProvider.getIfAvailable();
        if (configService == null) {
            throw new BizException("动态配置能力未装配，无法调整租户配额");
        }
        setIfPresent(configService, tenantId, TenantQuotaDimension.USERS, request.maxUsers());
        setIfPresent(configService, tenantId, TenantQuotaDimension.STORAGE, request.maxStorage());
        setIfPresent(configService, tenantId, TenantQuotaDimension.API_QPS, request.maxApiQps());
        setIfPresent(configService, tenantId, TenantQuotaDimension.API_DAILY, request.maxApiDaily());
        return quotaService.quotaOf(tenantId);
    }

    private void setIfPresent(DynamicConfigService configService, Long tenantId, TenantQuotaDimension dimension,
                              Long value) {
        if (value == null) {
            return;
        }
        configService.set(TenantQuotaService.QUOTA_KEY_PREFIX + tenantId + "." + dimension.getProperty(),
                String.valueOf(value), "tenant.quota");
    }

    public Map<String, Long> stats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", tenantMapper.selectCount(null));
        for (TenantStatus status : TenantStatus.values()) {
            stats.put(status.name(), tenantMapper.selectCount(
                    new LambdaQueryWrapper<Tenant>().eq(Tenant::getStatus, status)));
        }
        return stats;
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
