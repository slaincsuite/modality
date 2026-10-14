package cn.slainc.policy.storage;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.policy.domain.Policy;
import cn.slainc.policy.domain.PolicyStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryPolicyStore implements PolicyStore {

    private final Map<String, Policy> policies = new ConcurrentHashMap<>();

    private final Map<String, CopyOnWriteArrayList<PolicyVersionRecord>> versions = new ConcurrentHashMap<>();

    private final List<Audited> audits = new CopyOnWriteArrayList<>();

    private final AtomicLong auditSequence = new AtomicLong();

    private record Audited(long sequence, PolicyAuditRecord record) {
    }

    @Override
    public synchronized Policy save(Policy policy) {
        Policy existing = policies.get(policy.getId());
        long now = System.currentTimeMillis();
        if (existing == null) {
            policy.setVersion(1);
            policy.setCreatedAt(now);
            policy.setUpdatedAt(now);
            policies.put(policy.getId(), copy(policy));
            return copy(policy);
        }
        if (existing.getVersion() != policy.getVersion()) {
            throw new BizException(ResultCode.CONFLICT,
                    "策略 " + policy.getId() + " 已被他人修改（当前版本 " + existing.getVersion()
                            + "，提交基于版本 " + policy.getVersion() + "），请刷新后重试");
        }
        policy.setVersion(existing.getVersion() + 1);
        policy.setCreatedAt(existing.getCreatedAt());
        policy.setCreator(existing.getCreator());
        policy.setUpdatedAt(now);
        policies.put(policy.getId(), copy(policy));
        return copy(policy);
    }

    @Override
    public Optional<Policy> find(String id) {
        Policy policy = policies.get(id);
        return policy == null ? Optional.empty() : Optional.of(copy(policy));
    }

    @Override
    public List<Policy> list() {
        return policies.values().stream().sorted(Comparator.comparing(Policy::getCreatedAt)).map(this::copy).toList();
    }

    @Override
    public List<Policy> listEnabled() {
        return list().stream().filter(policy -> policy.getStatus() == PolicyStatus.ENABLED).toList();
    }

    @Override
    public synchronized void delete(String id) {
        policies.remove(id);
        versions.remove(id);
    }

    @Override
    public void archiveVersion(String policyId, int version, String document, String operator, int retainedVersions) {
        CopyOnWriteArrayList<PolicyVersionRecord> records = versions.computeIfAbsent(policyId, key -> new CopyOnWriteArrayList<>());
        records.removeIf(record -> record.getVersion() == version);
        PolicyVersionRecord record = new PolicyVersionRecord();
        record.setPolicyId(policyId);
        record.setVersion(version);
        record.setDocument(document);
        record.setOperator(operator);
        record.setCreatedAt(System.currentTimeMillis());
        records.add(record);
        if (retainedVersions > 0 && records.size() > retainedVersions) {
            records.sort(Comparator.comparingInt(PolicyVersionRecord::getVersion));
            int excess = records.size() - retainedVersions;
            for (PolicyVersionRecord removed : new ArrayList<>(records.subList(0, excess))) {
                records.remove(removed);
            }
        }
    }

    @Override
    public Optional<PolicyVersionRecord> findVersion(String policyId, int version) {
        return listVersions(policyId).stream().filter(record -> record.getVersion() == version).findFirst();
    }

    @Override
    public List<PolicyVersionRecord> listVersions(String policyId) {
        CopyOnWriteArrayList<PolicyVersionRecord> records = versions.get(policyId);
        if (records == null) {
            return List.of();
        }
        return records.stream().sorted(Comparator.comparingInt(PolicyVersionRecord::getVersion).reversed()).toList();
    }

    @Override
    public void appendAudit(PolicyAuditRecord record) {
        audits.add(new Audited(auditSequence.incrementAndGet(), record));
    }

    @Override
    public List<PolicyAuditRecord> listAudit(String policyId, int limit) {
        return audits.stream()
                .filter(audited -> policyId == null || policyId.equals(audited.record().getPolicyId()))
                .sorted(Comparator.comparingLong(Audited::sequence).reversed())
                .limit(limit <= 0 ? Long.MAX_VALUE : limit)
                .map(Audited::record)
                .toList();
    }

    private Policy copy(Policy policy) {
        Policy copied = new Policy();
        copied.setId(policy.getId());
        copied.setName(policy.getName());
        copied.setDescription(policy.getDescription());
        copied.setSubjectType(policy.getSubjectType());
        copied.setSubjectId(policy.getSubjectId());
        copied.setLoginType(policy.getLoginType());
        copied.setStatus(policy.getStatus());
        copied.setVersion(policy.getVersion());
        copied.setDocument(policy.getDocument());
        copied.setCreator(policy.getCreator());
        copied.setCreatedAt(policy.getCreatedAt());
        copied.setUpdatedAt(policy.getUpdatedAt());
        return copied;
    }
}
