package cn.slainc.modules.tenant.web;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditOperation;
import cn.slainc.modules.tenant.TenantKeys;
import cn.slainc.modules.tenant.dto.TenantPlanForm;
import cn.slainc.modules.tenant.dto.TenantPlanMenuForm;
import cn.slainc.modules.tenant.dto.TenantPlanView;
import cn.slainc.modules.tenant.service.TenantPlanService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant/plan")
@Tag(name = "租户套餐", description = "套餐模板（额度 / 功能开关 / 数据权限 / 默认菜单范围）管理")
public class TenantPlanController {

    private final TenantPlanService tenantPlanService;

    public TenantPlanController(TenantPlanService tenantPlanService) {
        this.tenantPlanService = tenantPlanService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_QUERY)
    public Result<PageResult<TenantPlanView>> page(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Boolean enabled,
                                                   @RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "10") long size) {
        return Result.ok(tenantPlanService.page(keyword, enabled, current, size));
    }

    @GetMapping("/list")
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_QUERY)
    public Result<List<TenantPlanView>> list() {
        return Result.ok(tenantPlanService.list());
    }

    @GetMapping("/{id}")
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_QUERY)
    public Result<TenantPlanView> detail(@PathVariable Long id) {
        return Result.ok(tenantPlanService.detail(id));
    }

    @GetMapping("/{id}/menus")
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_QUERY)
    public Result<List<Long>> menuIds(@PathVariable Long id) {
        return Result.ok(tenantPlanService.menuIds(id));
    }

    @PostMapping
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_CREATE)
    @AuditLog(value = "新增套餐", operation = AuditOperation.CREATE, category = AuditCategory.DATA, module = "tenant")
    public Result<TenantPlanView> create(@RequestBody TenantPlanForm form) {
        return Result.ok(tenantPlanService.create(form));
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_UPDATE)
    @AuditLog(value = "修改套餐", operation = AuditOperation.UPDATE, category = AuditCategory.DATA, module = "tenant")
    public Result<TenantPlanView> update(@PathVariable Long id, @RequestBody TenantPlanForm form) {
        return Result.ok(tenantPlanService.update(id, form));
    }

    @PutMapping("/{id}/status")
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_UPDATE)
    @AuditLog(value = "变更套餐状态", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "tenant")
    public Result<TenantPlanView> changeStatus(@PathVariable Long id, @RequestParam boolean enabled) {
        return Result.ok(tenantPlanService.changeStatus(id, enabled));
    }

    @PutMapping("/{id}/menus")
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_ASSIGN)
    @AuditLog(value = "分配套餐菜单", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "tenant")
    public Result<List<Long>> assignMenus(@PathVariable Long id, @RequestBody TenantPlanMenuForm form) {
        return Result.ok(tenantPlanService.assignMenus(id, form == null ? null : form.menuIds()));
    }

    @DeleteMapping("/{ids}")
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_DELETE)
    @AuditLog(value = "删除套餐", operation = AuditOperation.DELETE, category = AuditCategory.DATA, module = "tenant")
    public Result<Integer> delete(@PathVariable List<Long> ids) {
        return Result.ok(tenantPlanService.delete(ids));
    }
}
