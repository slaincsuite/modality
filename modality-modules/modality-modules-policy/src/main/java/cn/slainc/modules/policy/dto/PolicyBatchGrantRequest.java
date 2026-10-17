package cn.slainc.modules.policy.dto;

import java.util.List;

public record PolicyBatchGrantRequest(List<String> policyIds, String subjectType, List<String> subjectIds,
                                      String loginType) {
}
