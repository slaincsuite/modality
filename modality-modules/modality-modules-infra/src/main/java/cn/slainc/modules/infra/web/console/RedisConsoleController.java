package cn.slainc.modules.infra.web.console;

import cn.slainc.core.result.Result;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.console.RedisConsoleService;
import cn.slainc.security.annotation.RequirePermission;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Redis 控制台", description = "键空间查看与诊断")
@RestController
@RequestMapping("/infra/redis")
public class RedisConsoleController {

    private final RedisConsoleService consoleService;

    public RedisConsoleController(RedisConsoleService consoleService) {
        this.consoleService = consoleService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_REDIS_QUERY)
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(consoleService.stats());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_REDIS_QUERY)
    @GetMapping("/info")
    public Result<Map<String, String>> info(@RequestParam(required = false) String section) {
        return Result.ok(consoleService.info(section));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_REDIS_QUERY)
    @GetMapping("/keys")
    public Result<List<RedisConsoleService.KeyBrief>> keys(@RequestParam(required = false) String prefix,
                                                           @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(consoleService.keys(prefix, limit));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_REDIS_QUERY)
    @GetMapping("/key/{key}")
    public Result<Map<String, Object>> keyDetail(@PathVariable String key) {
        return Result.ok(consoleService.keyDetail(key));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_REDIS_DELETE)
    @DeleteMapping("/key/{key}")
    public Result<Boolean> deleteKey(@PathVariable String key) {
        return Result.ok(consoleService.deleteKey(key));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_REDIS_QUERY)
    @GetMapping("/slowlog")
    public Result<List<Map<String, Object>>> slowlog(@RequestParam(defaultValue = "10") int count) {
        return Result.ok(consoleService.slowlog(count));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_REDIS_QUERY)
    @GetMapping("/clients")
    public Result<List<Map<String, String>>> clients() {
        return Result.ok(consoleService.clients());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_REDIS_QUERY)
    @GetMapping("/cluster")
    public Result<Map<String, Object>> cluster() {
        return Result.ok(consoleService.cluster());
    }
}
