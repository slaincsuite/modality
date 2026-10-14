package cn.slainc.modules.tenant.web.stats;

import cn.slainc.core.result.Result;
import cn.slainc.modules.tenant.TenantKeys;
import cn.slainc.modules.tenant.service.manage.TenantManageService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/tenant")
@Tag(name = "租户统计", description = "租户按状态分布统计")
public class TenantStatsController {

    private final TenantManageService tenantManageService;

    public TenantStatsController(TenantManageService tenantManageService) {
        this.tenantManageService = tenantManageService;
    }

    @GetMapping("/stats")
    @RequirePermission(permissions = TenantKeys.PERMISSION_TENANT_QUERY)
    public Result<Map<String, Long>> stats() {
        return Result.ok(tenantManageService.stats());
    }
}
