package cn.slainc.modules.policy.dto;

import cn.slainc.modules.policy.entity.PolicyMeta;
import cn.slainc.policy.model.Policy;

public record PolicyView(String id, String name, String description, String subjectType, String subjectId,
                         String loginType, String status, int version, String document, String creator,
                         long createdAt, long updatedAt, String group, boolean system) {

    public static PolicyView of(Policy policy, PolicyMeta meta) {
        return new PolicyView(policy.getId(), policy.getName(), policy.getDescription(),
                policy.getSubjectType().code(), policy.getSubjectId(), policy.getLoginType(),
                policy.getStatus().code(), policy.getVersion(), policy.getDocument().toJson(),
                policy.getCreator(), policy.getCreatedAt(), policy.getUpdatedAt(),
                meta == null ? "默认" : meta.group(), meta != null && meta.system());
    }
}
