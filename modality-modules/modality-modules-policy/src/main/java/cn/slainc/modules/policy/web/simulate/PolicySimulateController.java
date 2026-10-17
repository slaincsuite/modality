package cn.slainc.modules.policy.web.simulate;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.dto.PolicyBatchSimulateRequest;
import cn.slainc.modules.policy.dto.PolicyCaseInput;
import cn.slainc.modules.policy.dto.PolicySimulateRequest;
import cn.slainc.modules.policy.dto.PolicyBoundaryView;
import cn.slainc.modules.policy.store.PolicyBoundaryStore;
import cn.slainc.policy.domain.PolicyDecision;
import cn.slainc.policy.domain.PolicyRequest;
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
import java.util.Optional;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "策略模拟", description = "策略命中模拟评估")
@RestController
@RequestMapping("/policy/simulate")
public class PolicySimulateController {

    private final PolicySimulator simulator;

    private final PolicyBoundaryStore boundaryStore;

    public PolicySimulateController(PolicySimulator simulator, PolicyBoundaryStore boundaryStore) {
        this.simulator = simulator;
        this.boundaryStore = boundaryStore;
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
        result.put("boundaries", decision.matches().stream().map(match -> match.policyId()).distinct()
                .map(boundaryStore::find).flatMap(Optional::stream)
                .map(boundary -> new PolicyBoundaryView(boundary.policyId(), boundary.document(),
                        boundary.operator(), boundary.createTime(), boundary.updateTime()))
                .toList());
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
        if (input == null || input.request() == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "批量模拟存在缺少请求内容的用例");
        }
        return new PolicyTestCase(input.name(), toRequest(input.request()), parseExpected(input.expected()));
    }

    private PolicyRequest toRequest(PolicySimulateRequest request) {
        if (request == null || request.action() == null || request.action().isBlank()
                || request.resource() == null || request.resource().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "模拟请求必须提供 action 与 resource");
        }
        PolicyRequest target = PolicyRequest.of(request.action(), request.resource());
        target.setUserId(request.userId());
        target.setLoginType(request.loginType());
        target.setTenantId(request.tenantId());
        if (request.roles() != null) {
            target.setRoles(request.roles());
        }
        if (request.groups() != null) {
            target.setGroups(request.groups());
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
