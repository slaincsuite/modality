package cn.slainc.modules.policy.dto;

public record BindingUserRequest(String policyId, Long userId, String loginType) {
}
