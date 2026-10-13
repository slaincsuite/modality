package cn.slainc.modules.infra.web;

import cn.slainc.core.result.Result;
import cn.slainc.modules.infra.InfraKeys;
import cn.slainc.modules.infra.service.SqlConsoleService;
import cn.slainc.security.annotation.RequirePermission;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "SQL 控制台", description = "SQL 监控与执行记录")
@RestController
@RequestMapping("/infra/sql")
public class SqlConsoleController {

    private final SqlConsoleService sqlConsoleService;

    public SqlConsoleController(SqlConsoleService sqlConsoleService) {
        this.sqlConsoleService = sqlConsoleService;
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SQL_QUERY)
    @GetMapping("/slow")
    public Result<List<?>> slow() {
        return Result.ok(sqlConsoleService.topSlow());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SQL_QUERY)
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(sqlConsoleService.stats());
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SQL_QUERY)
    @PostMapping("/explain")
    public Result<List<Map<String, Object>>> explain(@RequestBody Map<String, String> body) {
        return Result.ok(sqlConsoleService.explain(body.get("sql")));
    }

    @RequirePermission(permissions = InfraKeys.PERMISSION_SQL_QUERY)
    @PostMapping("/index-suggestions")
    public Result<List<String>> indexSuggestions(@RequestBody Map<String, String> body) {
        return Result.ok(sqlConsoleService.indexSuggestions(body.get("sql")));
    }
}
