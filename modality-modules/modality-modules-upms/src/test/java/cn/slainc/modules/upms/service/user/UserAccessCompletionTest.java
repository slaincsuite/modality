package cn.slainc.modules.upms.service.user;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.DeptRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.dept.DeptService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccessCompletionTest extends UpmsIntegrationTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private DeptService deptService;

    @Test
    void createSupportsRemarkGeneratedPasswordAndChangeSwitch() {
        UserRecord generated = userService.create("generated-password", "初始密码用户", null, null, null, null,
                LoginType.TENANT_USER, 1L, "创建备注", null, "admin");
        assertThat(generated.getRemark()).isEqualTo("创建备注");
        assertThat(generated.getInitialPassword()).hasSize(16)
                .containsPattern("[A-Z]").containsPattern("[a-z]").containsPattern("[0-9]");
        assertThat(generated.getMustChangePassword()).isTrue();

        UserRecord explicit = userService.create("explicit-password", null, "Explicit#12345", null, null, null,
                LoginType.TENANT_USER, 1L, null, false, "admin");
        assertThat(explicit.getInitialPassword()).isNull();
        assertThat(explicit.getMustChangePassword()).isFalse();
    }

    @Test
    void keywordSearchesAcrossIdentityFieldsAndKeepsIndependentFilters() {
        userService.create("lookup-name", "普通", null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        userService.create("other-1", "lookup-nick", null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        userService.create("other-2", null, null, null, "1390000lookup", null, LoginType.TENANT_USER, 1L,
                "admin");
        userService.create("other-3", null, null, "lookup@example.com", null, null, LoginType.TENANT_USER, 1L,
                "admin");

        PageResult<UserRecord> keyword = userService.search(null, null, null, null, null, null, null, null, null,
                null, null, "lookup", 1, 20);
        assertThat(keyword.total()).isEqualTo(4);
        assertThat(userService.search("lookup-name", null, null, null, null, null, null, null, null, null, null,
                "lookup", 1, 20).total()).isEqualTo(1);
        assertThat(userService.search(null, null, null, null, null, null, null, null, null, null, null,
                "missing-keyword", 1, 20).total()).isZero();
    }

    @Test
    void departmentCanBeClearedAndSecuritySwitchesPersist() {
        DeptRecord dept = deptService.create("待移出部门", null, null, 1);
        UserRecord user = userService.create("switch-user", null, null, null, null, dept.getId(),
                LoginType.TENANT_USER, 1L, "admin");
        assertThat(userService.updateDept(user.getId(), null).getDeptId()).isNull();
        assertThat(userService.changeConsoleAccess(user.getId(), false).getConsoleAccess()).isFalse();
        assertThat(userService.get(user.getId()).enabled()).isFalse();
        assertThat(userService.changeMustChangePassword(user.getId(), true).getMustChangePassword()).isTrue();
        assertThat(userService.get(user.getId()).getConsoleAccess()).isFalse();
    }

    @Test
    void crossTenantUserSearchRequiresSuperAdmin() {
        UserContext.set(new UserContext.UserInfo(900L, "root", LoginType.PLATFORM_ADMIN, Set.of(), Set.of(), true));
        UserRecord otherTenant = userService.create("tenant-two-user", null, null, null, null, null,
                LoginType.TENANT_USER, 2L, "admin");

        UserContext.set(UserContext.UserInfo.of(1L, "tenant-one", LoginType.TENANT_USER));
        assertThatThrownBy(() -> userService.searchForTenant(2L, "tenant-two", 1, 10))
                .isInstanceOf(BizException.class).hasMessageContaining("无权查看其他租户");
        assertThatThrownBy(() -> userService.get(otherTenant.getId()))
                .isInstanceOf(BizException.class).hasMessageContaining("用户不存在或已注销");

        UserContext.set(new UserContext.UserInfo(900L, "root", LoginType.PLATFORM_ADMIN, Set.of(), Set.of(), true));
        assertThat(userService.searchForTenant(2L, "tenant-two", 1, 10).total()).isEqualTo(1);
        assertThat(userService.searchForTenant(1L, null, 1, 10).total()).isZero();
    }
}
