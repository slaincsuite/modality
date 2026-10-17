package cn.slainc.modules.policy.store;

import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.policy.storage.PolicyBindingOrigin;
import cn.slainc.policy.storage.PolicyBindingOriginStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class PolicyOriginStore implements PolicyBindingOriginStore {

    private final JdbcTemplate jdbcTemplate;

    public PolicyOriginStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PolicyBindingOrigin> find(String sourcePolicyId, String subjectType, String subjectId,
                                              Long tenantId) {
        return jdbcTemplate.query("SELECT * FROM " + PolicyCenterKeys.POLICY_BINDING_SOURCE_TABLE
                                + " WHERE tenant_id = ? AND source_policy_id = ? AND subject_type = ?"
                                + " AND subject_id = ?",
                        PolicyOriginStore::mapRow, tenantId, sourcePolicyId, subjectType, subjectId)
                .stream().findFirst();
    }

    @Override
    public List<PolicyBindingOrigin> listBySource(String sourcePolicyId, Long tenantId) {
        return jdbcTemplate.query("SELECT * FROM " + PolicyCenterKeys.POLICY_BINDING_SOURCE_TABLE
                        + " WHERE tenant_id = ? AND source_policy_id = ? ORDER BY create_time DESC",
                PolicyOriginStore::mapRow, tenantId, sourcePolicyId);
    }

    @Override
    public void save(PolicyBindingOrigin origin) {
        try {
            jdbcTemplate.update("INSERT INTO " + PolicyCenterKeys.POLICY_BINDING_SOURCE_TABLE
                            + " (binding_id, policy_id, source_policy_id, subject_type, subject_id, tenant_id,"
                            + " create_time) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    origin.bindingId(), origin.policyId(), origin.sourcePolicyId(), origin.subjectType(),
                    origin.subjectId(), origin.tenantId(), origin.createTime());
        } catch (DuplicateKeyException ignored) {
            // 唯一约束兜底并发重复绑定
        }
    }

    @Override
    public void deleteByBindingId(Long bindingId) {
        jdbcTemplate.update("DELETE FROM " + PolicyCenterKeys.POLICY_BINDING_SOURCE_TABLE
                + " WHERE binding_id = ?", bindingId);
    }

    private static PolicyBindingOrigin mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PolicyBindingOrigin(rs.getLong("binding_id"), rs.getString("policy_id"),
                rs.getString("source_policy_id"), rs.getString("subject_type"), rs.getString("subject_id"),
                rs.getLong("tenant_id"), rs.getLong("create_time"));
    }
}
