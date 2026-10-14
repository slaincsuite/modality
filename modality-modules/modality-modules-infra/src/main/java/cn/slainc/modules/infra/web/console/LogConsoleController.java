package cn.slainc.modules.infra.web.console;

import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.Result;
import cn.slainc.logger.query.LogEntry;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.console.LogConsoleService;
import cn.slainc.security.annotation.RequirePermission;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "日志控制台", description = "运行日志在线检索")
@RestController
@RequestMapping("/infra/logs")
public class LogConsoleController {

    private final LogConsoleService logConsoleService;

    public LogConsoleController(LogConsoleService logConsoleService) {
        this.logConsoleService = logConsoleService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_LOG_QUERY)
    @GetMapping("/page")
    public Result<PageResult<LogEntry>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String level,
                                             @RequestParam(required = false) String loggerPrefix,
                                             @RequestParam(required = false) String traceId,
                                             @RequestParam(required = false) String from,
                                             @RequestParam(required = false) String to,
                                             @RequestParam(defaultValue = "false") boolean includeArchived) {
        return Result.ok(logConsoleService.page(current, size, keyword, level, loggerPrefix, traceId, from, to,
                includeArchived));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_LOG_QUERY)
    @GetMapping("/files")
    public Result<List<String>> files(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return Result.ok(logConsoleService.files(includeArchived));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_LOG_QUERY)
    @GetMapping("/download")
    public void download(@RequestParam String file, HttpServletResponse response) throws IOException {
        Path path = logConsoleService.download(file);
        response.setContentType(file.endsWith(".gz") ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"");
        response.setHeader("X-Content-Type-Options", "nosniff");
        logConsoleService.downloadTo(path, response.getOutputStream());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_LOG_LEVEL)
    @PutMapping("/level")
    public Result<Boolean> setLevel(@RequestBody Map<String, String> body) {
        return Result.ok(logConsoleService.setLevel(body.get("logger"), body.get("level")));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_LOG_LEVEL)
    @GetMapping("/level")
    public Result<String> getLevel(@RequestParam String logger) {
        return Result.ok(logConsoleService.getLevel(logger));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_LOG_LEVEL)
    @PostMapping("/level/refresh")
    public Result<Integer> refreshLevels() {
        return Result.ok(logConsoleService.refreshLevels());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_LOG_QUERY)
    @PostMapping("/archive")
    public Result<List<String>> archive() {
        return Result.ok(logConsoleService.archive());
    }
}
