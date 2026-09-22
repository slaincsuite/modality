package cn.slainc.modules.upms;

import cn.slainc.core.context.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public abstract class UpmsIntegrationTestBase {

    private static final String[] TABLES = {
            "t_user_login_record", "t_user_operation_log", "t_policy_binding", "t_access_control",
            "t_system_registry", "t_role_data_scope", "t_role_permission", "t_user_role",
            "t_user_group_member", "t_user_group", "t_dept", "t_menu", "t_permission", "t_role", "t_user",
            "t_policy_audit", "t_policy_version", "t_policy"
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    static {
        System.setProperty("sa-token.is-print", "false");
    }

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    @BeforeEach
    void resetTables() {
        TenantContext.set(1L);
        // 密码历史与权限缓存都是跨用例长 TTL 的 Redis 状态，用户 ID 复用时必须清掉避免读到上一轮数据
        for (String pattern : new String[]{"modality:security:pwd:hist:*", "modality:security:perm:*"}) {
            var keys = redis.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        for (String table : TABLES) {
            try {
                jdbcTemplate.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // policy 三张表按需懒建，首个用例前可能尚不存在
            }
        }
        jdbcTemplate.execute("ALTER TABLE t_user ALTER COLUMN id RESTART WITH 1");
    }
}
