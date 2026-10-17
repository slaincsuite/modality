package cn.slainc.modules.policy.web.generator;

import cn.slainc.core.context.UserContext;
import cn.slainc.logger.oplog.AuditCategory;
import cn.slainc.logger.oplog.AuditLog;
import cn.slainc.logger.oplog.AuditOperation;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.dto.PolicyGeneratorCapabilitiesView;
import cn.slainc.modules.policy.dto.PolicyGeneratorRequest;
import cn.slainc.modules.policy.dto.PolicyGeneratorView;
import cn.slainc.modules.policy.dto.PolicyView;
import cn.slainc.modules.policy.service.generator.PolicyGeneratorService;
import cn.slainc.security.annotation.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "策略生成器", description = "组合服务、动作、资源与条件生成强校验策略文档")
@RestController
@RequestMapping("/policy/generator")
public class PolicyGeneratorController {

    private final PolicyGeneratorService generatorService;

    public PolicyGeneratorController(PolicyGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @GetMapping("/capabilities")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_CATALOG_QUERY)
    public PolicyGeneratorCapabilitiesView capabilities() {
        return generatorService.capabilities();
    }

    @PostMapping("/preview")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_VALIDATE)
    public PolicyGeneratorView preview(@RequestBody PolicyGeneratorRequest request) {
        return generatorService.preview(request);
    }

    @PostMapping("/create")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_POLICY_CREATE)
    @AuditLog(value = "生成策略", operation = AuditOperation.CREATE, category = AuditCategory.DATA,
            module = "policy", recordParams = false)
    public PolicyView create(@RequestBody PolicyGeneratorRequest request) {
        return generatorService.create(request, UserContext.username());
    }
}
