package cn.slainc.modules.upms.web;

import cn.slainc.core.context.UserContext;
import cn.slainc.modules.upms.dto.PolicyBindClientRequest;
import cn.slainc.modules.upms.dto.PolicyBindRoleRequest;
import cn.slainc.modules.upms.dto.PolicyBindUserRequest;
import cn.slainc.modules.upms.entity.PolicyBindingRecord;
import cn.slainc.modules.upms.service.PolicyBindingService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/upms/policy-binding")
public class PolicyBindingController {

    private final PolicyBindingService bindingService;

    public PolicyBindingController(PolicyBindingService bindingService) {
        this.bindingService = bindingService;
    }

    @PostMapping("/user")
    @RequirePermission(permissions = "upms:policy:bind")
    public PolicyBindingRecord bindUser(@RequestBody PolicyBindUserRequest request) {
        return bindingService.bindToUser(request.policyId(), request.userId(), request.loginType(),
                UserContext.username());
    }

    @PostMapping("/role")
    @RequirePermission(permissions = "upms:policy:bind")
    public PolicyBindingRecord bindRole(@RequestBody PolicyBindRoleRequest request) {
        return bindingService.bindToRole(request.policyId(), request.roleCode(), UserContext.username());
    }

    @PostMapping("/client")
    @RequirePermission(permissions = "upms:policy:bind")
    public PolicyBindingRecord bindClient(@RequestBody PolicyBindClientRequest request) {
        return bindingService.bindToClient(request.policyId(), request.clientId(), UserContext.username());
    }

    @DeleteMapping("/{id}")
    @RequirePermission(permissions = "upms:policy:unbind")
    public void unbind(@PathVariable Long id) {
        bindingService.unbind(id);
    }

    @GetMapping("/query")
    @RequirePermission(permissions = "upms:policy:query")
    public List<PolicyBindingRecord> query(@RequestParam(required = false) String subjectType,
                                           @RequestParam(required = false) String subjectId,
                                           @RequestParam(required = false) String policyId) {
        return bindingService.query(subjectType, subjectId, policyId);
    }
}
