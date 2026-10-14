package cn.slainc.policy.storage;

import cn.slainc.policy.domain.Policy;

import java.util.List;
import java.util.Optional;

public interface PolicyStore {

    Policy save(Policy policy);

    Optional<Policy> find(String id);

    List<Policy> list();

    List<Policy> listEnabled();

    void delete(String id);

    void archiveVersion(String policyId, int version, String document, String operator, int retainedVersions);

    Optional<PolicyVersionRecord> findVersion(String policyId, int version);

    List<PolicyVersionRecord> listVersions(String policyId);

    void appendAudit(PolicyAuditRecord record);

    List<PolicyAuditRecord> listAudit(String policyId, int limit);
}
