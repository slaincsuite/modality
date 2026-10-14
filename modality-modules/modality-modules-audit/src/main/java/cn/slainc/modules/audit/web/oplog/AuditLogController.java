package cn.slainc.modules.audit.web.oplog;

import cn.slainc.modules.audit.AuditKeys;
import cn.slainc.modules.audit.service.apilog.ApiLogService;
import cn.slainc.modules.audit.excel.AuditLogExportService;
import cn.slainc.modules.audit.service.oplog.AuditLogService;
import cn.slainc.modules.audit.service.policylog.PolicyEvaluationLogService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.web.http.DownloadResponses;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;


@Tag(name = "审计日志", description = "审计事件查询与导出")
@RestController
@RequestMapping("/audit/logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    private final AuditLogExportService exportService;

    private final ApiLogService apiLogService;

    private final PolicyEvaluationLogService policyEvaluationLogService;

    public AuditLogController(AuditLogService auditLogService, AuditLogExportService exportService,
                              ApiLogService apiLogService,
                              PolicyEvaluationLogService policyEvaluationLogService) {
        this.auditLogService = auditLogService;
        this.exportService = exportService;
        this.apiLogService = apiLogService;
        this.policyEvaluationLogService = policyEvaluationLogService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = AuditKeys.PERMISSION_LOG_QUERY)
    public Object page(@RequestParam(required = false) String category,
                       @RequestParam(required = false) String operation,
                       @RequestParam(required = false) String level,
                       @RequestParam(required = false) String module,
                       @RequestParam(required = false) String username,
                       @RequestParam(required = false) String loginType,
                       @RequestParam(required = false) Boolean success,
                       @RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return auditLogService.search(category, operation, level, module, username, loginType, success, from, to,
                current, size);
    }

    @GetMapping("/stats")
    @RequirePermission(permissions = AuditKeys.PERMISSION_LOG_QUERY)
    public Object stats(@RequestParam(required = false) Long from,
                        @RequestParam(required = false) Long to,
                        @RequestParam(defaultValue = "10") int topUsers) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("login", auditLogService.loginStats(from, to));
        stats.put("loginTrend", auditLogService.loginTrend(from, to));
        stats.put("categoryDistribution", auditLogService.categoryDistribution(from, to));
        stats.put("activeUsers", auditLogService.activeUsers(from, to, topUsers));
        stats.put("api", apiLogService.requestStats(from, to));
        stats.put("policy", policyEvaluationLogService.decisionStats(from, to));
        return stats;
    }

    @GetMapping("/export")
    @RequirePermission(permissions = AuditKeys.PERMISSION_LOG_EXPORT)
    public void export(@RequestParam(required = false) String category,
                       @RequestParam(required = false) String operation,
                       @RequestParam(required = false) String level,
                       @RequestParam(required = false) String module,
                       @RequestParam(required = false) String username,
                       @RequestParam(required = false) String loginType,
                       @RequestParam(required = false) Boolean success,
                       @RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to,
                       @RequestParam(defaultValue = "10000") int limit,
                       HttpServletResponse response) {
        exportService.export(DownloadResponses.attachmentStream(response, "audit-logs.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL), category, operation, level, module, username, loginType,
                success, from, to, limit);
    }
}
