package cn.slainc.modules.tenant.web.lifecycle;

import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.tenant.TenantKeys;
import cn.slainc.modules.tenant.dto.TenantRenewRequest;
import cn.slainc.modules.tenant.service.manage.TenantManageService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.tenant.domain.Tenant;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@Tag(name = "租户生命周期", description = "租户续期、冻结、激活、停用与置为过期")
public class TenantLifecycleController {

    private final TenantManageService tenantManageService;

    public TenantLifecycleController(TenantManageService tenantManageService) {
        this.tenantManageService = tenantManageService;
    }

    @PostMapping("/{id}/renew")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_UPDATE)
    @AuditLog(value = "续期租户", operation = AuditOperation.UPDATE, category = AuditCategory.DATA, module = "tenant")
    public Result<Tenant> renew(@PathVariable Long id, @RequestBody TenantRenewRequest request) {
        return Result.ok(tenantManageService.renew(id, request.expireAt()));
    }

    @PostMapping("/{id}/freeze")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_UPDATE)
    @AuditLog(value = "冻结租户", operation = AuditOperation.UPDATE, category = AuditCategory.DATA, module = "tenant")
    public Result<Tenant> freeze(@PathVariable Long id) {
        return Result.ok(tenantManageService.freeze(id));
    }

    @PostMapping("/{id}/activate")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_UPDATE)
    @AuditLog(value = "激活租户", operation = AuditOperation.UPDATE, category = AuditCategory.DATA, module = "tenant")
    public Result<Tenant> activate(@PathVariable Long id) {
        return Result.ok(tenantManageService.activate(id));
    }

    @PostMapping("/{id}/disable")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_UPDATE)
    @AuditLog(value = "停用租户", operation = AuditOperation.UPDATE, category = AuditCategory.DATA, module = "tenant")
    public Result<Tenant> disable(@PathVariable Long id) {
        return Result.ok(tenantManageService.disable(id));
    }

    @PostMapping("/{id}/expire")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_UPDATE)
    @AuditLog(value = "置租户为过期", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "tenant")
    public Result<Tenant> expire(@PathVariable Long id) {
        return Result.ok(tenantManageService.expire(id));
    }
}
