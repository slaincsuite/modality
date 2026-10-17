package cn.slainc.policy.storage;

import cn.slainc.policy.domain.PolicyBinding;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryPolicyBindingStore implements PolicyBindingStore {

    private final Map<Long, PolicyBinding> bindings = new ConcurrentHashMap<>();

    private final AtomicLong sequence = new AtomicLong();

    @Override
    public synchronized PolicyBinding insert(PolicyBinding binding) {
        long id = sequence.incrementAndGet();
        binding.setId(id);
        bindings.put(id, copy(binding));
        return copy(binding);
    }

    @Override
    public PolicyBinding findById(Long id) {
        PolicyBinding binding = id == null ? null : bindings.get(id);
        return binding == null ? null : copy(binding);
    }

    @Override
    public synchronized void deleteById(Long id) {
        bindings.remove(id);
    }

    @Override
    public List<PolicyBinding> query(String subjectType, String subjectId, String policyId) {
        return bindings.values().stream()
                .filter(binding -> subjectType == null || subjectType.isBlank()
                        || subjectType.equals(binding.getSubjectType()))
                .filter(binding -> subjectId == null || subjectId.isBlank()
                        || subjectId.equals(binding.getSubjectId()))
                .filter(binding -> policyId == null || policyId.isBlank()
                        || policyId.equals(binding.getPolicyId()))
                .sorted(Comparator.comparing(PolicyBinding::getId).reversed())
                .map(this::copy)
                .toList();
    }

    private PolicyBinding copy(PolicyBinding binding) {
        PolicyBinding copied = new PolicyBinding();
        copied.setId(binding.getId());
        copied.setTenantId(binding.getTenantId());
        copied.setPolicyId(binding.getPolicyId());
        copied.setSourcePolicyId(binding.getSourcePolicyId());
        copied.setSubjectType(binding.getSubjectType());
        copied.setSubjectId(binding.getSubjectId());
        copied.setOperator(binding.getOperator());
        copied.setCreateTime(binding.getCreateTime());
        return copied;
    }
}
