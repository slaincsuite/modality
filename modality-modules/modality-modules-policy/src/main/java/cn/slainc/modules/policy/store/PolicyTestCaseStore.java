package cn.slainc.modules.policy.store;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.modules.policy.PolicyCenterKeys;
import cn.slainc.modules.policy.entity.PolicyTestCaseRecord;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public class PolicyTestCaseStore {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;

    public PolicyTestCaseStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PolicyTestCaseRecord insert(String policyId, String name, String description, String requestJson,
                                       String expected, boolean enabled) {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        String id = "tc_" + HexFormat.of().formatHex(bytes);
        long now = System.currentTimeMillis();
        jdbcTemplate.update("INSERT INTO " + PolicyCenterKeys.POLICY_TEST_CASE_TABLE
                        + " (id, policy_id, name, description, request_json, expected, enabled, create_time, update_time)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, policyId, name, description, requestJson, expected, enabled ? 1 : 0, now, now);
        return new PolicyTestCaseRecord(id, policyId, name, description, requestJson, expected, enabled, now, now);
    }

    public void update(String id, String policyId, String name, String description, String requestJson,
                       String expected, boolean enabled) {
        require(id);
        jdbcTemplate.update("UPDATE " + PolicyCenterKeys.POLICY_TEST_CASE_TABLE
                        + " SET policy_id = ?, name = ?, description = ?, request_json = ?, expected = ?,"
                        + " enabled = ?, update_time = ? WHERE id = ?",
                policyId, name, description, requestJson, expected, enabled ? 1 : 0, System.currentTimeMillis(), id);
    }

    public void delete(String id) {
        require(id);
        jdbcTemplate.update("DELETE FROM " + PolicyCenterKeys.POLICY_TEST_CASE_TABLE + " WHERE id = ?", id);
    }

    public Optional<PolicyTestCaseRecord> find(String id) {
        return jdbcTemplate.query("SELECT * FROM " + PolicyCenterKeys.POLICY_TEST_CASE_TABLE
                + " WHERE id = ?", PolicyTestCaseStore::mapRow, id).stream().findFirst();
    }

    public List<PolicyTestCaseRecord> list(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return jdbcTemplate.query("SELECT * FROM " + PolicyCenterKeys.POLICY_TEST_CASE_TABLE
                    + " ORDER BY create_time DESC", PolicyTestCaseStore::mapRow);
        }
        return jdbcTemplate.query("SELECT * FROM " + PolicyCenterKeys.POLICY_TEST_CASE_TABLE
                + " WHERE policy_id = ? ORDER BY create_time DESC", PolicyTestCaseStore::mapRow, policyId);
    }

    public List<PolicyTestCaseRecord> listEnabled(String policyId) {
        if (policyId == null || policyId.isBlank()) {
            return jdbcTemplate.query("SELECT * FROM " + PolicyCenterKeys.POLICY_TEST_CASE_TABLE
                    + " WHERE enabled = 1 ORDER BY create_time DESC", PolicyTestCaseStore::mapRow);
        }
        return jdbcTemplate.query("SELECT * FROM " + PolicyCenterKeys.POLICY_TEST_CASE_TABLE
                + " WHERE enabled = 1 AND policy_id = ? ORDER BY create_time DESC",
                PolicyTestCaseStore::mapRow, policyId);
    }

    private void require(String id) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + PolicyCenterKeys.POLICY_TEST_CASE_TABLE + " WHERE id = ?",
                Long.class, id);
        if (count == null || count == 0) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "测试用例 " + id + " 不存在");
        }
    }

    private static PolicyTestCaseRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PolicyTestCaseRecord(rs.getString("id"), rs.getString("policy_id"), rs.getString("name"),
                rs.getString("description"), rs.getString("request_json"), rs.getString("expected"),
                rs.getInt("enabled") == 1, rs.getLong("create_time"), rs.getLong("update_time"));
    }
}
