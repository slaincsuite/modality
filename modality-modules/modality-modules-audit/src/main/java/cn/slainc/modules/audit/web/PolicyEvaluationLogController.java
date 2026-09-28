package cn.slainc.modules.audit.web;

import cn.slainc.modules.audit.AuditKeys;
import cn.slainc.modules.audit.service.PolicyEvaluationLogService;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/audit/policy-logs")
public class PolicyEvaluationLogController {

    private final PolicyEvaluationLogService logService;

    public PolicyEvaluationLogController(PolicyEvaluationLogService logService) {
        this.logService = logService;
    }

    @GetMapping("/page")
    @RequirePermission(permissions = AuditKeys.PERMISSION_POLICY_LOG_QUERY)
    public Object page(@RequestParam(required = false) String loginId,
                       @RequestParam(required = false) String loginType,
                       @RequestParam(required = false) String action,
                       @RequestParam(required = false) String resource,
                       @RequestParam(required = false) String decision,
                       @RequestParam(required = false) Long from,
                       @RequestParam(required = false) Long to,
                       @RequestParam(defaultValue = "1") long current,
                       @RequestParam(defaultValue = "10") long size) {
        return logService.search(loginId, loginType, action, resource, decision, from, to, current, size);
    }

    @GetMapping("/stats")
    @RequirePermission(permissions = AuditKeys.PERMISSION_POLICY_LOG_QUERY)
    public Object stats(@RequestParam(required = false) Long from,
                        @RequestParam(required = false) Long to) {
        return logService.decisionStats(from, to);
    }
}
