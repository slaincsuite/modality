package cn.slainc.modules.auth.store;

import cn.slainc.modules.auth.AuthKeys;
import cn.slainc.modules.auth.policy.SecurityPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

public class SecurityPolicyStore {

    private static final String TABLE_NAME = AuthKeys.SECURITY_POLICY_TABLE;

    private static final RowMapper<SecurityPolicy> MAPPER = (rs, rowNum) -> {
        SecurityPolicy policy = new SecurityPolicy();
        policy.setId(rs.getLong("id"));
        policy.setLoginProtect(rs.getBoolean("login_protect"));
        policy.setOperationProtect(rs.getBoolean("operation_protect"));
        policy.setRemoteCheck(rs.getBoolean("remote_check"));
        policy.setIdleTimeoutSeconds(rs.getLong("idle_timeout_seconds"));
        policy.setMaxSessionSeconds(rs.getLong("max_session_seconds"));
        policy.setCreateTime(rs.getLong("create_time"));
        policy.setUpdateTime(rs.getLong("update_time"));
        return policy;
    };

    private final JdbcTemplate jdbcTemplate;

    public SecurityPolicyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SecurityPolicy find() {
        List<SecurityPolicy> policies = jdbcTemplate.query(
                "SELECT * FROM " + TABLE_NAME + " WHERE id = ?", MAPPER, SecurityPolicy.DEFAULT_ID);
        return policies.isEmpty() ? null : policies.get(0);
    }

    public SecurityPolicy save(SecurityPolicy policy) {
        long now = System.currentTimeMillis();
        if (policy.getCreateTime() == null) {
            policy.setCreateTime(now);
        }
        policy.setUpdateTime(now);
        if (find() == null) {
            try {
                jdbcTemplate.update("INSERT INTO " + TABLE_NAME
                                + " (id, login_protect, operation_protect, remote_check, idle_timeout_seconds,"
                                + " max_session_seconds, create_time, update_time) VALUES (?,?,?,?,?,?,?,?)",
                        SecurityPolicy.DEFAULT_ID, policy.getLoginProtect(), policy.getOperationProtect(),
                        policy.getRemoteCheck(), policy.getIdleTimeoutSeconds(), policy.getMaxSessionSeconds(),
                        policy.getCreateTime(), policy.getUpdateTime());
                policy.setId(SecurityPolicy.DEFAULT_ID);
                return policy;
            } catch (DuplicateKeyException ignored) {
                // 并发初始化时由另一个请求完成插入，随后走更新分支
            }
        }
        jdbcTemplate.update("UPDATE " + TABLE_NAME
                        + " SET login_protect = ?, operation_protect = ?, remote_check = ?,"
                        + " idle_timeout_seconds = ?, max_session_seconds = ?, update_time = ? WHERE id = ?",
                policy.getLoginProtect(), policy.getOperationProtect(), policy.getRemoteCheck(),
                policy.getIdleTimeoutSeconds(), policy.getMaxSessionSeconds(), policy.getUpdateTime(),
                SecurityPolicy.DEFAULT_ID);
        policy.setId(SecurityPolicy.DEFAULT_ID);
        return policy;
    }
}
