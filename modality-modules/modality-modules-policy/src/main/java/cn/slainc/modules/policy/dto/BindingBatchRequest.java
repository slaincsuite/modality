package cn.slainc.modules.policy.dto;

import java.util.List;

public record BindingBatchRequest(String policyId, String subjectType, List<String> subjectIds,
                                  String loginType) {
}
