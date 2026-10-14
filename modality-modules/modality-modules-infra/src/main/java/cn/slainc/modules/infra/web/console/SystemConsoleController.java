package cn.slainc.modules.infra.web.console;

import cn.slainc.core.result.Result;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.console.SystemConsoleService;
import cn.slainc.security.annotation.RequirePermission;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "系统监控", description = "JVM 与主机运行指标")
@RestController
@RequestMapping("/infra/system")
public class SystemConsoleController {

    private final SystemConsoleService systemConsoleService;

    public SystemConsoleController(SystemConsoleService systemConsoleService) {
        this.systemConsoleService = systemConsoleService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SYSTEM_QUERY)
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(systemConsoleService.health());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SYSTEM_QUERY)
    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics() {
        return Result.ok(systemConsoleService.metrics());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SYSTEM_ALERT)
    @GetMapping("/alerts/rules")
    public Result<Map<String, Object>> alertRules() {
        return Result.ok(systemConsoleService.alertRules());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SYSTEM_ALERT)
    @PostMapping("/alerts/test")
    public Result<Integer> sendTestAlert(@RequestBody(required = false) Map<String, String> body) {
        return Result.ok(systemConsoleService.sendTestAlert(body == null ? null : body.get("level"),
                body == null ? null : body.get("message")));
    }
}
