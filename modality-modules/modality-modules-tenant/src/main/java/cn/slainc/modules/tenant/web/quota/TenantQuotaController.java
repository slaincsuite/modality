package cn.slainc.modules.tenant.web.quota;

import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.tenant.TenantKeys;
import cn.slainc.modules.tenant.dto.TenantQuotaRequest;
import cn.slainc.modules.tenant.service.manage.TenantManageService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.tenant.quota.TenantQuota;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@Tag(name = "租户配额", description = "租户四维配额（用户数、存储、接口 QPS、接口日调用量）查询与调整")
public class TenantQuotaController {

    private final TenantManageService tenantManageService;

    public TenantQuotaController(TenantManageService tenantManageService) {
        this.tenantManageService = tenantManageService;
    }

    @GetMapping("/{id}/quota")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_QUERY)
    public Result<TenantQuota> quota(@PathVariable Long id) {
        return Result.ok(tenantManageService.quota(id));
    }

    @PutMapping("/{id}/quota")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_UPDATE)
    @AuditLog(value = "调整租户配额", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "tenant")
    public Result<TenantQuota> adjustQuota(@PathVariable Long id, @RequestBody TenantQuotaRequest request) {
        return Result.ok(tenantManageService.adjustQuota(id, request));
    }
}
