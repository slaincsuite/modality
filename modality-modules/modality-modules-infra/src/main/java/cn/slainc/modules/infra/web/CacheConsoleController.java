package cn.slainc.modules.infra.web;

import cn.slainc.core.result.Result;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.CacheConsoleService;
import cn.slainc.security.annotation.RequirePermission;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/infra/cache")
public class CacheConsoleController {

    private final CacheConsoleService cacheConsoleService;

    public CacheConsoleController(CacheConsoleService cacheConsoleService) {
        this.cacheConsoleService = cacheConsoleService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CACHE_QUERY)
    @GetMapping("/names")
    public Result<List<Map<String, Object>>> names() {
        return Result.ok(cacheConsoleService.names());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CACHE_QUERY)
    @GetMapping("/describe")
    public Result<Map<String, Object>> describe() {
        return Result.ok(cacheConsoleService.describe());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CACHE_CLEAR)
    @DeleteMapping("/{name}")
    public Result<Long> clear(@PathVariable String name) {
        return Result.ok(cacheConsoleService.clear(name));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_CACHE_CLEAR)
    @PostMapping("/warm")
    public Result<Map<String, Object>> warm() {
        return Result.ok(cacheConsoleService.warm());
    }
}
