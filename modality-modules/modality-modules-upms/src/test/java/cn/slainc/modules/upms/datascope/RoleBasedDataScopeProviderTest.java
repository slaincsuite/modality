package cn.slainc.modules.upms.datascope;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.DeptRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.dept.DeptService;
import cn.slainc.modules.upms.service.role.RoleService;
import cn.slainc.modules.upms.service.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleBasedDataScopeProviderTest extends UpmsIntegrationTestBase {

    @Autowired
    private ScopeDemoMapper demoMapper;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserService userService;

    @Autowired
    private DeptService deptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long deptA;

    private Long deptA1;

    private Long deptB;

    @BeforeEach
    void seedDemoRows() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_scope_demo ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "tenant_id BIGINT NOT NULL DEFAULT 1,"
                + "dept_id BIGINT,"
                + "created_by BIGINT,"
                + "title VARCHAR(64))");
        jdbcTemplate.execute("DELETE FROM t_scope_demo");
        deptA = deptService.create("数据权限A部", null, null, null).getId();
        deptA1 = deptService.create("数据权限A1组", deptA, null, null).getId();
        deptB = deptService.create("数据权限B部", null, null, null).getId();
        // created_by 用 901+ 避开 t_user 自增序列（重置到 1 起），否则 SELF 兜底条件会误命中种子行
        insertRow(deptA, 901L, "A-部-他人");
        insertRow(deptA1, 902L, "A1-组-他人");
        insertRow(deptB, 903L, "B-部-他人");
        insertRow(deptA, 904L, "A-部-标记");
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    private void insertRow(Long deptId, Long createdBy, String title) {
        jdbcTemplate.update("INSERT INTO t_scope_demo(tenant_id, dept_id, created_by, title) VALUES (1,?,?,?)",
                deptId, createdBy, title);
    }

    private Long loginAs(String username, String scope, List<Long> customDepts) {
        RoleRecord role = roleService.create("scope-" + scope + "-" + username, "范围角色", null, scope, null, null,
                null);
        if ("CUSTOM".equals(scope) && customDepts != null && !customDepts.isEmpty()) {
            roleService.setDataScope(role.getId(), "CUSTOM", customDepts);
        }
        UserRecord user = userService.create(username, null, "Scope#12345", null, null, deptA, LoginType.TENANT_USER,
                1L, "admin");
        userService.assignRoles(user.getId(), List.of(role.getId()));
        UserContext.set(UserContext.UserInfo.of(user.getId(), username, LoginType.TENANT_USER));
        return user.getId();
    }

    @Test
    void allScopeSeesEverything() {
        Long userId = loginAs("scope-all", "ALL", null);
        assertThat(demoMapper.selectWithScope()).hasSize(4);
        assertThat(userId).isNotNull();
    }

    @Test
    void deptScopeSeesOwnDeptOnly() {
        loginAs("scope-dept", "DEPT", null);
        assertThat(demoMapper.selectWithScope()).extracting(ScopeDemoRecord::getTitle)
                .containsExactlyInAnyOrder("A-部-他人", "A-部-标记");
    }

    @Test
    void deptAndChildScopeSeesSubtree() {
        loginAs("scope-child", "DEPT_AND_CHILD", null);
        assertThat(demoMapper.selectWithScope()).extracting(ScopeDemoRecord::getTitle)
                .containsExactlyInAnyOrder("A-部-他人", "A-部-标记", "A1-组-他人");
    }

    @Test
    void selfScopeSeesOnlyOwnRows() {
        RoleRecord role = roleService.create("scope-self-role", "本人", null, "SELF", null, null, null);
        UserRecord user = userService.create("scope-self", null, "Scope#12345", null, null, deptA,
                LoginType.TENANT_USER, 1L, "admin");
        userService.assignRoles(user.getId(), List.of(role.getId()));
        UserContext.set(UserContext.UserInfo.of(user.getId(), "scope-self", LoginType.TENANT_USER));
        assertThat(demoMapper.selectWithScope()).extracting(ScopeDemoRecord::getTitle).isEmpty();
        jdbcTemplate.update("INSERT INTO t_scope_demo(tenant_id, dept_id, created_by, title) VALUES (1,?,?,'自己新建')",
                deptB, user.getId());
        assertThat(demoMapper.selectWithScope()).extracting(ScopeDemoRecord::getTitle)
                .containsExactly("自己新建");
    }

    @Test
    void customScopeSeesConfiguredDepts() {
        loginAs("scope-custom", "CUSTOM", List.of(deptB));
        assertThat(demoMapper.selectWithScope()).extracting(ScopeDemoRecord::getTitle)
                .containsExactly("B-部-他人");
    }

    @Test
    void superAdminBypassesScope() {
        Long userId = loginAs("scope-admin", "SELF", null);
        UserContext.set(new UserContext.UserInfo(userId, "scope-admin", LoginType.PLATFORM_ADMIN, null, null,
                true));
        assertThat(demoMapper.selectWithScope()).hasSize(4);
    }

    @Test
    void userWithoutRoleFallsBackToSelf() {
        UserRecord user = userService.create("scope-none", null, "Scope#12345", null, null, deptA,
                LoginType.TENANT_USER, 1L, "admin");
        UserContext.set(UserContext.UserInfo.of(user.getId(), "scope-none", LoginType.TENANT_USER));
        assertThat(demoMapper.selectWithScope()).isEmpty();
    }
}
