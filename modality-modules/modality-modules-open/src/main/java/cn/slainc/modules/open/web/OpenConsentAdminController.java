package cn.slainc.modules.open.web;

import cn.slainc.core.context.UserContext;
import cn.slainc.logger.audit.AuditCategory;
import cn.slainc.logger.audit.AuditLog;
import cn.slainc.logger.audit.AuditOperation;
import cn.slainc.modules.open.OpenKeys;
import cn.slainc.modules.open.excel.ConsentExportService;
import cn.slainc.modules.open.service.OpenConsentAdminService;
import cn.slainc.security.annotation.RequirePermission;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/open/admin/consents")
public class OpenConsentAdminController {

    private final OpenConsentAdminService consentAdminService;

    private final ConsentExportService exportService;

    public OpenConsentAdminController(OpenConsentAdminService consentAdminService,
                                      ConsentExportService exportService) {
        this.consentAdminService = consentAdminService;
        this.exportService = exportService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = OpenKeys.PERMISSION_CONSENT_QUERY)
    public Object page(@RequestParam(required = false) String userId,
                       @RequestParam(required = false) String appId,
                       @RequestParam(required = false) String scope,
                       @RequestParam(required = false) Boolean active,
                       @RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return consentAdminService.page(userId, appId, scope, active, from, to, current, size);
    }

    @GetMapping("/export")
    @RequirePermission(permissions = OpenKeys.PERMISSION_CONSENT_EXPORT)
    public void export(@RequestParam(required = false) String userId,
                       @RequestParam(required = false) String appId,
                       @RequestParam(required = false) Boolean active,
                       @RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to,
                       @RequestParam(defaultValue = "10000") int limit,
                       HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=open-consents.xlsx");
        exportService.export(response.getOutputStream(), userId, appId, active, from, to, limit);
    }

    @DeleteMapping("/{userId}/{appId}")
    @RequirePermission(permissions = OpenKeys.PERMISSION_CONSENT_REVOKE)
    @AuditLog(value = "强制解约授权记录", operation = AuditOperation.DELETE, category = AuditCategory.PERMISSION,
            module = "open")
    public Object forceRevoke(@PathVariable String userId, @PathVariable String appId) {
        return consentAdminService.forceRevoke(userId, appId, UserContext.username());
    }

    @PostMapping("/{appId}/batch-revoke")
    @RequirePermission(permissions = OpenKeys.PERMISSION_CONSENT_REVOKE)
    @AuditLog(value = "按应用批量解约", operation = AuditOperation.DELETE, category = AuditCategory.PERMISSION,
            module = "open")
    public Object batchRevoke(@PathVariable String appId) {
        return consentAdminService.batchRevokeByApp(appId, UserContext.username());
    }
}
