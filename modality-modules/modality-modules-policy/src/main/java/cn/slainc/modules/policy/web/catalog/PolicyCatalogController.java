package cn.slainc.modules.policy.web.catalog;

import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.service.catalog.PolicyCatalogService;
import cn.slainc.security.annotation.CheckMode;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "策略目录", description = "策略服务、动作与资源元数据目录")
@RestController
@RequestMapping("/policy")
public class PolicyCatalogController {

    private final PolicyCatalogService catalogService;

    public PolicyCatalogController(PolicyCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_CATALOG_QUERY,
            PolicyCenterKeys.PERMISSION_POLICY_QUERY}, mode = CheckMode.ANY)
    public Object products() {
        return catalogService.products();
    }

    @GetMapping("/services")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_CATALOG_QUERY,
            PolicyCenterKeys.PERMISSION_POLICY_QUERY}, mode = CheckMode.ANY)
    public Object services() {
        return catalogService.services();
    }

    @GetMapping("/services/{code}/actions")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_CATALOG_QUERY,
            PolicyCenterKeys.PERMISSION_POLICY_QUERY}, mode = CheckMode.ANY)
    public Object actions(@PathVariable String code) {
        return catalogService.actions(code);
    }

    @GetMapping("/resources/tree")
    @RequirePermission(permissions = {PolicyCenterKeys.PERMISSION_POLICY_CATALOG_QUERY,
            PolicyCenterKeys.PERMISSION_POLICY_QUERY}, mode = CheckMode.ANY)
    public Object resources() {
        return catalogService.resources();
    }
}
