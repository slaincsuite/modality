package cn.slainc.modules.upms.web;

import cn.slainc.modules.upms.UpmsKeys;

import cn.slainc.modules.upms.excel.OperationLogExportService;
import cn.slainc.modules.upms.log.OperationLogService;
import cn.slainc.security.annotation.RequirePermission;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

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
    public void export(@RequestParam(defaultValue = "10000") int limit, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=operation-logs.xlsx");
        exportService.export(response.getOutputStream(), limit);
    }
}
