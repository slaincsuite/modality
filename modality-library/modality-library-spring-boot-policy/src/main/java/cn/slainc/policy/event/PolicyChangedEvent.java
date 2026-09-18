package cn.slainc.policy.event;

public record PolicyChangedEvent(String policyId, String operation, long timestamp) {
}
