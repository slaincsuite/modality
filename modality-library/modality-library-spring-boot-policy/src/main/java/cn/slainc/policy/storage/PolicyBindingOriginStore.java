package cn.slainc.policy.storage;

import java.util.List;
import java.util.Optional;

public interface PolicyBindingOriginStore {

    Optional<PolicyBindingOrigin> find(String sourcePolicyId, String subjectType, String subjectId, Long tenantId);

    List<PolicyBindingOrigin> listBySource(String sourcePolicyId, Long tenantId);

    default Optional<PolicyBindingOrigin> findByPolicyId(String policyId, Long tenantId) {
        return Optional.empty();
    }

    void save(PolicyBindingOrigin origin);

    void deleteByBindingId(Long bindingId);
}
