package cn.slainc.modules.tenant.web;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.tenant.TenantKeys;
import cn.slainc.modules.tenant.service.TenantManageService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.tenant.domain.Tenant;
import cn.slainc.web.http.DownloadResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tenant")
@Tag(name = "租户查询", description = "租户分页、列表、详情、按编码查询、编码占用校验与 Excel 导出")
public class TenantQueryController {

    private final TenantManageService tenantManageService;

    public TenantQueryController(TenantManageService tenantManageService) {
        this.tenantManageService = tenantManageService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_QUERY)
    public Result<PageResult<Tenant>> page(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(tenantManageService.page(status, keyword, current, size));
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

    @GetMapping("/export")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_EXPORT)
    @AuditLog(value = "导出租户列表", operation = AuditOperation.EXPORT, category = AuditCategory.DATA,
            module = "tenant")
    public void export(@RequestParam(required = false) String status,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response) {
        tenantManageService.exportExcel(DownloadResponses.attachmentStream(response, "tenants.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL), status, keyword);
    }
}
