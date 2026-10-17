package cn.slainc.modules.policy.dto;

public record BindingGroupRequest(String policyId, String groupId, String subjectId) {

    public String groupKey() {
        return groupId == null || groupId.isBlank() ? subjectId : groupId;
    }
}
