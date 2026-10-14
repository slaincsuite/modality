package cn.slainc.modules.policy.service.testcase;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.policy.dto.PolicyTestCaseRequest;
import cn.slainc.modules.policy.entity.PolicyTestCaseRecord;
import cn.slainc.modules.policy.store.PolicyTestCaseStore;
import cn.slainc.policy.domain.PolicyDecision;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.policy.simulate.PolicySimulator;
import cn.slainc.policy.simulate.PolicyTestCase;
import cn.slainc.policy.simulate.PolicyTestReport;
import cn.slainc.utils.json.JsonUtils;

import java.util.ArrayList;
import java.util.List;

public class PolicyTestCaseService {

    private final PolicyTestCaseStore store;

    private final PolicySimulator simulator;

    public PolicyTestCaseService(PolicyTestCaseStore store, PolicySimulator simulator) {
        this.store = store;
        this.simulator = simulator;
    }

    public PolicyTestCaseRecord create(PolicyTestCaseRequest request) {
        validate(request);
        return store.insert(request.policyId(), request.name().trim(), request.description(),
                request.requestJson(), normalizeExpected(request.expected()), request.enabled());
    }

    public PolicyTestCaseRecord update(String id, PolicyTestCaseRequest request) {
        validate(request);
        store.update(id, request.policyId(), request.name().trim(), request.description(),
                request.requestJson(), normalizeExpected(request.expected()), request.enabled());
        return store.find(id).orElseThrow();
    }

    public void delete(String id) {
        store.delete(id);
    }

    public PolicyTestCaseRecord get(String id) {
        return store.find(id).orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND,
                "测试用例 " + id + " 不存在"));
    }

    public PageResult<PolicyTestCaseRecord> page(String policyId, long current, long size) {
        return PageResult.from(store.list(policyId), current, size);
    }

    public PolicyTestReport run(String policyId) {
        List<PolicyTestCase> cases = new ArrayList<>();
        for (PolicyTestCaseRecord record : store.listEnabled(policyId)) {
            cases.add(new PolicyTestCase(record.name(), parseRequest(record), parseExpected(record.expected())));
        }
        return simulator.runCases(cases);
    }

    public PolicyTestReport runOne(String id) {
        PolicyTestCaseRecord record = store.find(id)
                .orElseThrow(() -> new BizException(ResultCode.DATA_NOT_FOUND, "测试用例 " + id + " 不存在"));
        return simulator.runCases(List.of(new PolicyTestCase(record.name(), parseRequest(record),
                parseExpected(record.expected()))));
    }

    private PolicyRequest parseRequest(PolicyTestCaseRecord record) {
        return JsonUtils.parse(record.requestJson(), PolicyRequest.class);
    }

    private void validate(PolicyTestCaseRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("测试用例名称不能为空");
        }
        PolicyRequest parsed = JsonUtils.parse(request.requestJson(), PolicyRequest.class);
        if (parsed == null || parsed.getAction() == null || parsed.getResource() == null) {
            throw new IllegalArgumentException("测试用例的请求体必须包含 action 与 resource");
        }
        parseExpected(request.expected());
    }

    private PolicyDecision.Decision parseExpected(String expected) {
        return PolicyDecision.Decision.valueOf(normalizeExpected(expected));
    }

    private String normalizeExpected(String expected) {
        String upper = expected == null ? "" : expected.trim().toUpperCase();
        return switch (upper) {
            case "ALLOW", "允许" -> "ALLOW";
            case "DENY", "拒绝" -> "DENY";
            case "NOT_APPLICABLE", "NA", "未命中" -> "NOT_APPLICABLE";
            default -> throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "期望结果只允许 ALLOW、DENY、NOT_APPLICABLE，当前为 " + expected);
        };
    }
}
