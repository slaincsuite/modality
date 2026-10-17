package cn.slainc.modules.tenant.web.login;

import cn.slainc.core.result.Result;
import cn.slainc.tenant.domain.Tenant;
import cn.slainc.tenant.lookup.TenantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Tag(name = "登录租户", description = "登录前可选的可用租户")
@RestController
@RequestMapping("/tenant")
public class LoginTenantController {

    private final ObjectProvider<TenantService> tenantService;

    public LoginTenantController(ObjectProvider<TenantService> tenantService) {
        this.tenantService = tenantService;
    }

    public record TenantOption(Long id, String code, String name) {
    }

    @GetMapping("/login-options")
    public Result<List<TenantOption>> tenants() {
        TenantService service = tenantService.getIfAvailable();
        if (service == null) {
            return Result.ok(List.of());
        }
        LocalDateTime now = LocalDateTime.now();
        List<TenantOption> options = service.list().stream()
                .filter(tenant -> tenant.isUsable(now))
                .sorted(Comparator.comparing(Tenant::getId))
                .map(tenant -> new TenantOption(tenant.getId(), tenant.getTenantCode(), tenant.getTenantName()))
                .toList();
        return Result.ok(options);
    }
}
