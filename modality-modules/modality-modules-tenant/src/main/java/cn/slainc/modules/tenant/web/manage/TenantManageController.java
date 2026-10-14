package cn.slainc.modules.tenant.web.manage;

import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.tenant.TenantKeys;
import cn.slainc.modules.tenant.dto.TenantCreateRequest;
import cn.slainc.modules.tenant.dto.TenantUpdateRequest;
import cn.slainc.modules.tenant.service.manage.TenantManageService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.tenant.domain.Tenant;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@Tag(name = "租户管理", description = "租户开通、试用、资料修改与注销")
public class TenantManageController {

    private final TenantManageService tenantManageService;

    public TenantManageController(TenantManageService tenantManageService) {
        this.tenantManageService = tenantManageService;
    }

    @PostMapping
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_CREATE)
    @AuditLog(value = "开通租户", operation = AuditOperation.CREATE, category = AuditCategory.DATA, module = "tenant")
    public Result<Tenant> create(@RequestBody TenantCreateRequest request) {
        return Result.ok(tenantManageService.create(request));
    }

    @PostMapping("/trial")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_CREATE)
    @AuditLog(value = "开通试用租户", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "tenant")
    public Result<Tenant> trial(@RequestBody TenantCreateRequest request) {
        return Result.ok(tenantManageService.trial(request));
    }

    @PutMapping("/{id}")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_UPDATE)
    @AuditLog(value = "修改租户资料", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "tenant")
    public Result<Tenant> update(@PathVariable Long id, @RequestBody TenantUpdateRequest request) {
        return Result.ok(tenantManageService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_DELETE)
    @AuditLog(value = "注销租户", operation = AuditOperation.DELETE, category = AuditCategory.DATA, module = "tenant")
    public Result<Void> delete(@PathVariable Long id) {
        tenantManageService.delete(id);
        return Result.ok();
    }
}
