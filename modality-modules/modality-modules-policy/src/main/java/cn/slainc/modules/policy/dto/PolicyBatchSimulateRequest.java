package cn.slainc.modules.policy.dto;

import java.util.List;

public record PolicyBatchSimulateRequest(List<PolicyCaseInput> cases) {
}
