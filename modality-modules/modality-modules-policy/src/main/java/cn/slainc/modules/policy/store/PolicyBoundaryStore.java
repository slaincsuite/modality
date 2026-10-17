package cn.slainc.modules.policy.store;

import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.entity.PolicyBoundaryRecord;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class PolicyBoundaryStore {

    private final JdbcTemplate jdbcTemplate;

    public PolicyBoundaryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String policyId, String document, String operator) {
        long now = System.currentTimeMillis();
        Optional<PolicyBoundaryRecord> existing = find(policyId);
        if (existing.isPresent()) {
            jdbcTemplate.update("UPDATE " + PolicyCenterKeys.POLICY_BOUNDARY_TABLE
                            + " SET document = ?, operator = ?, update_time = ? WHERE policy_id = ?",
                    document, operator, now, policyId);
            return;
        }
        jdbcTemplate.update("INSERT INTO " + PolicyCenterKeys.POLICY_BOUNDARY_TABLE
                        + " (policy_id, document, operator, create_time, update_time) VALUES (?, ?, ?, ?, ?)",
                policyId, document, operator, now, now);
    }

    public Optional<PolicyBoundaryRecord> find(String policyId) {
        return jdbcTemplate.query("SELECT * FROM " + PolicyCenterKeys.POLICY_BOUNDARY_TABLE
                        + " WHERE policy_id = ?", PolicyBoundaryStore::mapRow, policyId)
                .stream().findFirst();
    }

    public void delete(String policyId) {
        jdbcTemplate.update("DELETE FROM " + PolicyCenterKeys.POLICY_BOUNDARY_TABLE + " WHERE policy_id = ?",
                policyId);
    }

    private static PolicyBoundaryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PolicyBoundaryRecord(rs.getString("policy_id"), rs.getString("document"),
                rs.getString("operator"), rs.getLong("create_time"), rs.getLong("update_time"));
    }
}
