package cn.slainc.modules.upms.web.log;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.modules.upms.excel.OperationLogExportService;
import cn.slainc.modules.upms.log.OperationLogService;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.web.http.DownloadResponses;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "操作日志", description = "操作流水查询")
@RestController
@RequestMapping("/upms/operation-log")
public class OperationLogController {

    private final OperationLogService operationLogService;

    private final OperationLogExportService exportService;

    public OperationLogController(OperationLogService operationLogService, OperationLogExportService exportService) {
        this.operationLogService = operationLogService;
        this.exportService = exportService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_LOG_QUERY)
    public Object page(@RequestParam(required = false) Long userId, @RequestParam(required = false) String username,
                       @RequestParam(required = false) String module, @RequestParam(required = false) String operation,
                       @RequestParam(required = false) Boolean success, @RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to, @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return operationLogService.search(userId, username, module, operation, success, from, to, current, size);
    }

    @GetMapping("/export")
    @RequirePermission(permissions = UpmsKeys.PERMISSION_UPMS_LOG_EXPORT)
    public void export(@RequestParam(defaultValue = "10000") int limit, HttpServletResponse response) {
        exportService.export(DownloadResponses.attachmentStream(response, "operation-logs.xlsx",
                DownloadResponses.CONTENT_TYPE_EXCEL), limit);
    }
}
