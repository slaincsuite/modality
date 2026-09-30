package cn.slainc.modules.policy.web;

import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.dto.PolicyBatchSimulateRequest;
import cn.slainc.modules.policy.dto.PolicyCaseInput;
import cn.slainc.modules.policy.dto.PolicySimulateRequest;
import cn.slainc.policy.model.PolicyDecision;
import cn.slainc.policy.model.PolicyRequest;
import cn.slainc.policy.simulate.PolicySimulator;
import cn.slainc.policy.simulate.PolicyTestCase;
import cn.slainc.policy.simulate.PolicyTestReport;
import cn.slainc.security.annotation.RequirePermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/policy/simulate")
public class PolicySimulateController {

    private final PolicySimulator simulator;

    public PolicySimulateController(PolicySimulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_SIMULATE_RUN)
    public Object simulate(@RequestBody PolicySimulateRequest request) {
        long start = System.nanoTime();
        PolicyDecision decision = simulator.simulate(toRequest(request));
        long costMillis = (System.nanoTime() - start) / 1_000_000;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decision", decision.decision().name());
        result.put("allowed", decision.allowed());
        result.put("reason", decision.reason());
        result.put("matches", decision.matches());
        result.put("explain", simulator.explain(decision));
        result.put("costMillis", costMillis);
        return result;
    }

    @PostMapping("/batch")
    @RequirePermission(permissions = PolicyCenterKeys.PERMISSION_SIMULATE_RUN)
    public Object batch(@RequestBody PolicyBatchSimulateRequest request) {
        List<PolicyTestCase> cases = request.cases() == null ? List.of() : request.cases().stream()
                .map(this::toCase)
                .toList();
        PolicyTestReport report = simulator.runCases(cases);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", report.total());
        result.put("passed", report.passed());
        result.put("results", report.results());
        result.put("summary", report.summary());
        return result;
    }

    private PolicyTestCase toCase(PolicyCaseInput input) {
        return new PolicyTestCase(input.name(), toRequest(input.request()), parseExpected(input.expected()));
    }

    private PolicyRequest toRequest(PolicySimulateRequest request) {
        if (request.action() == null || request.resource() == null) {
            throw new IllegalArgumentException("模拟请求必须提供 action 与 resource");
        }
        PolicyRequest target = PolicyRequest.of(request.action(), request.resource());
        target.setUserId(request.userId());
        target.setLoginType(request.loginType());
        target.setTenantId(request.tenantId());
        if (request.roles() != null) {
            target.setRoles(request.roles());
        }
        if (request.env() != null) {
            request.env().forEach(target::env);
        }
        return target;
    }

    private PolicyDecision.Decision parseExpected(String expected) {
        return switch (expected == null ? "NOT_APPLICABLE" : expected.trim().toUpperCase()) {
            case "ALLOW" -> PolicyDecision.Decision.ALLOW;
            case "DENY" -> PolicyDecision.Decision.DENY;
            default -> PolicyDecision.Decision.NOT_APPLICABLE;
        };
    }
}
