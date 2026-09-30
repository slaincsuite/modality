package cn.slainc.modules.policy.dto;

import java.util.List;
import java.util.Map;

public record PolicySimulateRequest(String action, String resource, String userId, String loginType,
                                    Long tenantId, List<String> roles, Map<String, Object> env) {
}
