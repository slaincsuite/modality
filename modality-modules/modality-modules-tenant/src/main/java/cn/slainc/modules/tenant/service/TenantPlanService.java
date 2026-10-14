package cn.slainc.modules.tenant.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.tenant.dto.TenantPlanForm;
import cn.slainc.modules.tenant.dto.TenantPlanView;
import cn.slainc.modules.tenant.entity.TenantPlanMenuRecord;
import cn.slainc.modules.tenant.entity.TenantPlanRecord;
import cn.slainc.modules.tenant.mapper.TenantPlanMapper;
import cn.slainc.modules.tenant.mapper.TenantPlanMenuMapper;
import cn.slainc.modules.tenant.plan.TenantPlanAdapter;
import cn.slainc.modules.tenant.plan.TenantPlanLimits;
import cn.slainc.tenant.domain.Tenant;
import cn.slainc.tenant.dao.TenantMapper;
import cn.slainc.tenant.lookup.TenantService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// 租户套餐：模板 CRUD、默认菜单范围，以及租户绑定与生效值查询
public class TenantPlanService {

    private final TenantPlanMapper planMapper;

    private final TenantPlanMenuMapper planMenuMapper;

    private final ObjectProvider<TenantMapper> tenantMapperProvider;

    private final ObjectProvider<TenantService> tenantServiceProvider;

    private final ObjectProvider<TenantPlanAdapter> adapterProvider;

    public TenantPlanService(TenantPlanMapper planMapper, TenantPlanMenuMapper planMenuMapper,
                             ObjectProvider<TenantMapper> tenantMapperProvider,
                             ObjectProvider<TenantService> tenantServiceProvider,
                             ObjectProvider<TenantPlanAdapter> adapterProvider) {
        this.planMapper = planMapper;
        this.planMenuMapper = planMenuMapper;
        this.tenantMapperProvider = tenantMapperProvider;
        this.tenantServiceProvider = tenantServiceProvider;
        this.adapterProvider = adapterProvider;
    }

    public PageResult<TenantPlanView> page(String keyword, Boolean enabled, long current, long size) {
        String trimmed = keyword == null ? "" : keyword.trim();
        LambdaQueryWrapper<TenantPlanRecord> wrapper = new LambdaQueryWrapper<TenantPlanRecord>()
                .eq(enabled != null, TenantPlanRecord::getEnabled, enabled)
                .and(!trimmed.isEmpty(), w -> w.like(TenantPlanRecord::getPlanCode, trimmed)
                        .or()
                        .like(TenantPlanRecord::getPlanName, trimmed))
                .orderByAsc(TenantPlanRecord::getSort)
                .orderByAsc(TenantPlanRecord::getId);
        Page<TenantPlanRecord> page = planMapper.selectPage(Page.of(current, size), wrapper);
        return PageResult.of(page.getRecords().stream().map(TenantPlanView::from).toList(), page.getTotal(),
                page.getCurrent(), page.getSize());
    }

    public List<TenantPlanView> list() {
        return planMapper.selectList(Wrappers.<TenantPlanRecord>lambdaQuery()
                        .orderByAsc(TenantPlanRecord::getSort)
                        .orderByAsc(TenantPlanRecord::getId)).stream()
                .map(TenantPlanView::from)
                .toList();
    }

    public TenantPlanView detail(Long id) {
        return TenantPlanView.from(require(id));
    }

    public TenantPlanView create(TenantPlanForm form) {
        TenantPlanForm safe = requireForm(form);
        TenantPlanRecord record = new TenantPlanRecord();
        record.setPlanCode(requireCode(safe.planCode()));
        record.setPlanName(requireName(safe.planName()));
        applyQuotas(record, safe);
        record.setSort(safe.sort() == null ? 0 : safe.sort());
        record.setEnabled(safe.enabled() == null || safe.enabled());
        record.setRemark(safe.remark());
        record.setCreateTime(System.currentTimeMillis());
        record.setUpdateTime(record.getCreateTime());
        checkCodeUnique(record.getPlanCode(), null);
        planMapper.insert(record);
        evictAllAfterChange();
        return detail(record.getId());
    }

    public TenantPlanView update(Long id, TenantPlanForm form) {
        TenantPlanForm safe = requireForm(form);
        TenantPlanRecord exists = require(id);
        TenantPlanRecord patch = new TenantPlanRecord();
        patch.setId(id);
        patch.setPlanCode(requireCode(safe.planCode()));
        patch.setPlanName(requireName(safe.planName()));
        checkCodeUnique(patch.getPlanCode(), id);
        applyQuotas(patch, safe);
        patch.setSort(safe.sort());
        patch.setEnabled(safe.enabled());
        patch.setRemark(safe.remark());
        patch.setUpdateTime(System.currentTimeMillis());
        planMapper.updateById(patch);
        evictAllAfterChange();
        return detail(id);
    }

    public TenantPlanView changeStatus(Long id, boolean enabled) {
        require(id);
        TenantPlanRecord patch = new TenantPlanRecord();
        patch.setId(id);
        patch.setEnabled(enabled);
        patch.setUpdateTime(System.currentTimeMillis());
        planMapper.updateById(patch);
        evictAllAfterChange();
        return detail(id);
    }

    public int delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        planMenuMapper.delete(Wrappers.<TenantPlanMenuRecord>lambdaQuery().in(TenantPlanMenuRecord::getPlanId, ids));
        int deleted = planMapper.deleteByIds(ids);
        evictAllAfterChange();
        return deleted;
    }

    public List<Long> menuIds(Long id) {
        require(id);
        return planMenuIds(id);
    }

    public List<Long> assignMenus(Long id, List<Long> menuIds) {
        require(id);
        Set<Long> distinct = distinct(menuIds);
        planMenuMapper.delete(Wrappers.<TenantPlanMenuRecord>lambdaQuery().eq(TenantPlanMenuRecord::getPlanId, id));
        long now = System.currentTimeMillis();
        for (Long menuId : distinct) {
            TenantPlanMenuRecord link = new TenantPlanMenuRecord();
            link.setPlanId(id);
            link.setMenuId(menuId);
            link.setCreateTime(now);
            planMenuMapper.insert(link);
        }
        evictAllAfterChange();
        return List.copyOf(distinct);
    }

    // 绑定套餐（planId 为空表示解绑）；modality 用户菜单为平台级表，omni 的「套餐菜单覆盖租户授权」不适用，默认菜单仅随生效值输出
    public TenantPlanLimits bind(Long tenantId, Long planId) {
        TenantService tenantService = tenantService();
        tenantService.requireById(tenantId);
        TenantPlanRecord plan = planId == null ? null : require(planId);
        if (plan != null && Boolean.FALSE.equals(plan.getEnabled())) {
            throw new BizException(ResultCode.DATA_STATE_INVALID, "套餐已停用，不能绑定");
        }
        // updateById 会跳过 null 字段，解绑必须用 UpdateWrapper 显式置空 package_id
        tenantMapper().update(null, Wrappers.<Tenant>lambdaUpdate()
                .eq(Tenant::getId, tenantId)
                .set(Tenant::getPackageId, planId));
        tenantService.evict(tenantId);
        TenantPlanAdapter adapter = adapterProvider.getIfAvailable();
        if (adapter != null) {
            // 先同步失效，保证返回的是刚绑定的生效值
            adapter.evict(tenantId);
        }
        return effective(tenantId);
    }

    public TenantPlanLimits effective(Long tenantId) {
        tenantService().requireById(tenantId);
        TenantPlanAdapter adapter = adapterProvider.getIfAvailable();
        if (adapter == null) {
            throw new BizException("未启用租户套餐解析");
        }
        return adapter.load(tenantId);
    }

    private void applyQuotas(TenantPlanRecord record, TenantPlanForm form) {
        record.setMaxUsers(form.maxUsers());
        record.setStorageQuotaMb(form.storageQuotaMb());
        record.setMaxFileSizeMb(form.maxFileSizeMb());
        record.setMaxExportRows(form.maxExportRows());
        record.setLogRetentionDays(form.logRetentionDays());
        record.features(form.features());
        record.setRateLimitPerMinute(form.rateLimitPerMinute());
        record.setMaxConcurrentExports(form.maxConcurrentExports());
        record.setSearchMaxPageSize(form.searchMaxPageSize());
        record.dataScope(form.dataScope());
    }

    private List<Long> planMenuIds(Long planId) {
        return planMenuMapper.selectList(Wrappers.<TenantPlanMenuRecord>lambdaQuery()
                        .eq(TenantPlanMenuRecord::getPlanId, planId)
                        .orderByAsc(TenantPlanMenuRecord::getMenuId)).stream()
                .map(TenantPlanMenuRecord::getMenuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private void evictAllAfterChange() {
        TenantPlanAdapter adapter = adapterProvider.getIfAvailable();
        if (adapter != null) {
            adapter.evictAll();
        }
    }

    private TenantService tenantService() {
        TenantService tenantService = tenantServiceProvider.getIfAvailable();
        if (tenantService == null) {
            throw new BizException("未启用租户存储");
        }
        return tenantService;
    }

    private TenantMapper tenantMapper() {
        TenantMapper tenantMapper = tenantMapperProvider.getIfAvailable();
        if (tenantMapper == null) {
            throw new BizException("未启用租户存储");
        }
        return tenantMapper;
    }

    private TenantPlanRecord require(Long id) {
        TenantPlanRecord record = id == null ? null : planMapper.selectById(id);
        if (record == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "套餐不存在");
        }
        return record;
    }

    private static TenantPlanForm requireForm(TenantPlanForm form) {
        if (form == null) {
            throw new BizException("套餐不能为空");
        }
        return form;
    }

    private static String requireCode(String planCode) {
        String value = planCode == null ? null : planCode.trim();
        if (value == null || value.isEmpty()) {
            throw new BizException("套餐编码不能为空");
        }
        if (value.length() > 64) {
            throw new BizException("套餐编码长度不能超过64");
        }
        return value;
    }

    private static String requireName(String planName) {
        String value = planName == null ? null : planName.trim();
        if (value == null || value.isEmpty()) {
            throw new BizException("套餐名称不能为空");
        }
        if (value.length() > 64) {
            throw new BizException("套餐名称长度不能超过64");
        }
        return value;
    }

    private void checkCodeUnique(String planCode, Long excludeId) {
        Long count = planMapper.selectCount(Wrappers.<TenantPlanRecord>lambdaQuery()
                .eq(TenantPlanRecord::getPlanCode, planCode)
                .ne(excludeId != null, TenantPlanRecord::getId, excludeId));
        if (count != null && count > 0L) {
            throw new BizException(ResultCode.DATA_DUPLICATE, "套餐编码已存在：" + planCode);
        }
    }

    private static Set<Long> distinct(List<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>();
        if (ids != null) {
            ids.stream().filter(Objects::nonNull).forEach(distinct::add);
        }
        return distinct;
    }
}
