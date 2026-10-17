package cn.slainc.modules.auth.dto;

public record SessionPolicyRequest(Long idleTimeoutSeconds, Long maxSessionSeconds) {
}
