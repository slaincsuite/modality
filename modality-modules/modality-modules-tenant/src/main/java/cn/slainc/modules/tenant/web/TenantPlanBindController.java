package cn.slainc.modules.tenant.web;

import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.tenant.TenantKeys;
import cn.slainc.modules.tenant.dto.TenantPlanBindForm;
import cn.slainc.modules.tenant.plan.TenantPlanLimits;
import cn.slainc.modules.tenant.service.TenantPlanService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@Tag(name = "租户套餐绑定", description = "租户绑定 / 解绑套餐与套餐生效值查询")
public class TenantPlanBindController {

    private final TenantPlanService tenantPlanService;

    public TenantPlanBindController(TenantPlanService tenantPlanService) {
        this.tenantPlanService = tenantPlanService;
    }

    @GetMapping("/{id}/plan")
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_QUERY)
    public Result<TenantPlanLimits> effective(@PathVariable Long id) {
        return Result.ok(tenantPlanService.effective(id));
    }

    @PutMapping("/{id}/plan")
    @RequirePermission(permissions = TenantKeys.PERMISSION_PLAN_BIND)
    @AuditLog(value = "绑定租户套餐", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "tenant")
    public Result<TenantPlanLimits> bind(@PathVariable Long id, @RequestBody TenantPlanBindForm form) {
        return Result.ok(tenantPlanService.bind(id, form == null ? null : form.planId()));
    }
}
