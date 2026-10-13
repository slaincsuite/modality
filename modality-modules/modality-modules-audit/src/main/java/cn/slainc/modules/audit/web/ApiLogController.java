package cn.slainc.modules.audit.web;

import cn.slainc.modules.audit.AuditKeys;
import cn.slainc.modules.audit.service.ApiLogService;
import cn.slainc.modules.audit.excel.ApiLogExportService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.web.http.DownloadResponses;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;


@Tag(name = "API 日志", description = "接口调用流水查询")
@RestController
@RequestMapping("/audit/api-logs")
public class ApiLogController {

    private final ApiLogService apiLogService;

    private final ApiLogExportService exportService;

    public ApiLogController(ApiLogService apiLogService, ApiLogExportService exportService) {
        this.apiLogService = apiLogService;
        this.exportService = exportService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = AuditKeys.PERMISSION_API_LOG_QUERY)
    public Object page(@RequestParam(required = false) String path,
                       @RequestParam(required = false) String method,
                       @RequestParam(required = false) Integer statusCode,
                       @RequestParam(required = false) String loginId,
                       @RequestParam(required = false) String loginType,
                       @RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return apiLogService.search(path, method, statusCode, loginId, loginType, from, to, current, size);
    }

    @GetMapping("/slow")
    @RequirePermission(permissions = AuditKeys.PERMISSION_API_LOG_QUERY)
    public Object slow(@RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return apiLogService.slowRequests(from, to, current, size);
    }

    @GetMapping("/errors")
    @RequirePermission(permissions = AuditKeys.PERMISSION_API_LOG_QUERY)
    public Object errors(@RequestParam(required = false) Long from,
                         @RequestParam(required = false) Long to,
                         @RequestParam(defaultValue = "1") long current,
                         @RequestParam(defaultValue = "10") long size) {
        return apiLogService.errorRequests(from, to, current, size);
    }

    @GetMapping("/export")
    @RequirePermission(permissions = AuditKeys.PERMISSION_API_LOG_EXPORT)
    public void export(@RequestParam(required = false) String path,
                       @RequestParam(required = false) String method,
                       @RequestParam(required = false) Integer statusCode,
                       @RequestParam(required = false) String loginId,
                       @RequestParam(required = false) String loginType,
                       @RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to,
                       @RequestParam(defaultValue = "10000") int limit,
                       HttpServletResponse response) {
        exportService.export(DownloadResponses.attachmentStream(response, "api-logs.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL), path, method, statusCode, loginId, loginType, from, to,
                limit);
    }
}
