package cn.slainc.modules.audit.web;

import cn.slainc.modules.audit.AuditKeys;
import cn.slainc.modules.audit.service.AuditCleanupService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/audit/maintenance")
public class AuditMaintenanceController {

    private final AuditCleanupService cleanupService;

    public AuditMaintenanceController(AuditCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @PostMapping("/cleanup")
    @RequirePermission(permissions = AuditKeys.PERMISSION_MAINTENANCE)
    public Object cleanup() {
        return cleanupService.cleanup();
    }
}
