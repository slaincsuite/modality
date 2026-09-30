package cn.slainc.modules.policy.store;

import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.entity.PolicyMeta;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PolicyMetaStore {

    private final JdbcTemplate jdbcTemplate;

    public PolicyMetaStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(String policyId, String group, boolean system) {
        String groupName = group == null || group.isBlank() ? PolicyCenterKeys.DEFAULT_GROUP : group.trim();
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + PolicyCenterKeys.POLICY_META_TABLE + " WHERE policy_id = ?",
                Long.class, policyId);
        long now = System.currentTimeMillis();
        if (count != null && count > 0) {
            jdbcTemplate.update("UPDATE " + PolicyCenterKeys.POLICY_META_TABLE
                    + " SET group_name = ?, system_flag = ?, update_time = ? WHERE policy_id = ?",
                    groupName, system ? 1 : 0, now, policyId);
            return;
        }
        jdbcTemplate.update("INSERT INTO " + PolicyCenterKeys.POLICY_META_TABLE
                + " (policy_id, group_name, system_flag, create_time, update_time) VALUES (?, ?, ?, ?, ?)",
                policyId, groupName, system ? 1 : 0, now, now);
    }

    public void updateGroup(String policyId, String group) {
        String groupName = group == null || group.isBlank() ? PolicyCenterKeys.DEFAULT_GROUP : group.trim();
        jdbcTemplate.update("UPDATE " + PolicyCenterKeys.POLICY_META_TABLE
                + " SET group_name = ?, update_time = ? WHERE policy_id = ?",
                groupName, System.currentTimeMillis(), policyId);
    }

    public Optional<PolicyMeta> find(String policyId) {
        List<PolicyMeta> found = jdbcTemplate.query(
                "SELECT policy_id, group_name, system_flag FROM " + PolicyCenterKeys.POLICY_META_TABLE
                        + " WHERE policy_id = ?",
                PolicyMetaStore::mapRow, policyId);
        return found.stream().findFirst();
    }

    public Map<String, PolicyMeta> listAll() {
        Map<String, PolicyMeta> indexed = new HashMap<>();
        jdbcTemplate.query("SELECT policy_id, group_name, system_flag FROM "
                + PolicyCenterKeys.POLICY_META_TABLE, rs -> {
            PolicyMeta meta = mapRow(rs, 0);
            indexed.put(meta.policyId(), meta);
        });
        return indexed;
    }

    public void delete(String policyId) {
        jdbcTemplate.update("DELETE FROM " + PolicyCenterKeys.POLICY_META_TABLE + " WHERE policy_id = ?", policyId);
    }

    private static PolicyMeta mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PolicyMeta(rs.getString("policy_id"), rs.getString("group_name"), rs.getInt("system_flag") == 1);
    }
}
