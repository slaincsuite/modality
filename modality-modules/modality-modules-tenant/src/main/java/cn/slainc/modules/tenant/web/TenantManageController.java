package cn.slainc.modules.tenant.web;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditOperation;
import cn.slainc.modules.tenant.TenantKeys;
import cn.slainc.modules.tenant.dto.TenantCreateRequest;
import cn.slainc.modules.tenant.dto.TenantQuotaRequest;
import cn.slainc.modules.tenant.dto.TenantRenewRequest;
import cn.slainc.modules.tenant.dto.TenantUpdateRequest;
import cn.slainc.modules.tenant.excel.TenantExcelService;
import cn.slainc.modules.tenant.service.TenantManageService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.tenant.entity.Tenant;
import cn.slainc.tenant.quota.TenantQuota;
import cn.slainc.web.http.DownloadResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
import java.util.Map;

@RestController
@RequestMapping("/tenant")
@Tag(name = "租户管理", description = "租户生命周期（开通、试用、续期、冻结、激活、停用、过期、注销）、资料与配额管理端点")
public class TenantManageController {

    private final TenantManageService tenantManageService;

    private final TenantExcelService tenantExcelService;

    public TenantManageController(TenantManageService tenantManageService, TenantExcelService tenantExcelService) {
        this.tenantManageService = tenantManageService;
        this.tenantExcelService = tenantExcelService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_QUERY)
    public Result<PageResult<Tenant>> page(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(tenantManageService.page(status, keyword, current, size));
    }

    @GetMapping("/stats")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_QUERY)
    public Result<Map<String, Long>> stats() {
        return Result.ok(tenantManageService.stats());
    }

    @GetMapping("/list")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_QUERY)
    public Result<List<Tenant>> list() {
        return Result.ok(tenantManageService.list());
    }

    @GetMapping("/exists")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_QUERY)
    public Result<Boolean> exists(@RequestParam String code) {
        return Result.ok(tenantManageService.codeExists(code));
    }

    @GetMapping("/code/{code}")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_QUERY)
    public Result<Tenant> byCode(@PathVariable String code) {
        return Result.ok(tenantManageService.requireByCode(code));
    }

    @GetMapping("/{id}")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_QUERY)
    public Result<Tenant> detail(@PathVariable Long id) {
        return Result.ok(tenantManageService.require(id));
    }

    @GetMapping("/{id}/quota")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_QUERY)
    public Result<TenantQuota> quota(@PathVariable Long id) {
        return Result.ok(tenantManageService.quota(id));
    }

    @GetMapping("/export")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_EXPORT)
    @AuditLog(value = "导出租户列表", operation = AuditOperation.EXPORT, category = AuditCategory.DATA,
            module = "tenant")
    public void export(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response) {
        tenantExcelService.export(DownloadResponses.attachmentStream(response, "tenants.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL), tenantManageService.listForExport(status, keyword));
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

    @PutMapping("/{id}/quota")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_UPDATE)
    @AuditLog(value = "调整租户配额", operation = AuditOperation.UPDATE, category = AuditCategory.DATA,
            module = "tenant")
    public Result<TenantQuota> adjustQuota(@PathVariable Long id, @RequestBody TenantQuotaRequest request) {
        return Result.ok(tenantManageService.adjustQuota(id, request));
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

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_DELETE)
    @AuditLog(value = "注销租户", operation = AuditOperation.DELETE, category = AuditCategory.DATA, module = "tenant")
    public Result<Void> delete(@PathVariable Long id) {
        tenantManageService.delete(id);
        return Result.ok();
    }
}
