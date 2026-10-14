package cn.slainc.policy.storage;

import cn.slainc.policy.domain.PolicyBinding;

import java.util.List;

public interface PolicyBindingStore {

    PolicyBinding insert(PolicyBinding binding);

    PolicyBinding findById(Long id);

    void deleteById(Long id);

    List<PolicyBinding> query(String subjectType, String subjectId, String policyId);
}
