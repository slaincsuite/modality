package cn.slainc.modules.upms.service.role;

import cn.slainc.modules.upms.service.dept.DeptService;
import cn.slainc.modules.upms.service.permission.PermissionService;
import cn.slainc.modules.upms.service.user.UserService;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.core.enums.LoginType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleServiceTest extends UpmsIntegrationTestBase {

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserService userService;

    @Test
    void createRoleWithValidations() {
        assertThatThrownBy(() -> roleService.create(null, "名", null, null, null, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("不能为空");
        assertThatThrownBy(() -> roleService.create("coder", "码农", null, "EVERYTHING", null, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("非法的数据权限范围");
        RoleRecord role = roleService.create("coder", "码农", null, "DEPT", 3, "dev", "备注");
        assertThat(role.getDataScope()).isEqualTo("DEPT");
        assertThatThrownBy(() -> roleService.create("coder", "重复", null, null, null, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("已存在");
    }

    @Test
    void inheritanceChainGrantsParentPermissionsToChild() {
        PermissionRecord read = permissionService.create("doc:read", "读文档", "API", null, null, null);
        PermissionRecord write = permissionService.create("doc:write", "写文档", "API", null, null, null);
        RoleRecord parent = roleService.create("manager", "经理", null, null, null, null, null);
        RoleRecord child = roleService.create("coder", "码农", parent.getId(), null, null, null, null);
        roleService.assignPermissions(parent.getId(), List.of(read.getId()));
        roleService.assignPermissions(child.getId(), List.of(write.getId()));
        UserRecord user = userService.create("nina", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        userService.assignRoles(user.getId(), List.of(child.getId()));
        assertThat(roleService.resolveRoleCodes(user.getId())).containsExactlyInAnyOrder("coder", "manager");
        assertThat(roleService.resolvePermissionCodes(user.getId()))
                .containsExactlyInAnyOrder("doc:read", "doc:write");
    }

    @Test
    void selfParentRejected() {
        RoleRecord role = roleService.create("lead", "组长", null, null, null, null, null);
        assertThatThrownBy(() -> roleService.update(role.getId(), null, role.getId(), null, null, null, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("不能是自身");
    }

    @Test
    void updateRejectsDescendantAsParent() {
        RoleRecord parent = roleService.create("tree-parent", "树父", null, null, null, null, null);
        RoleRecord child = roleService.create("tree-child", "树子", parent.getId(), null, null, null, null);
        RoleRecord grandChild = roleService.create("tree-grand", "树孙", child.getId(), null, null, null, null);
        assertThatThrownBy(() -> roleService.update(parent.getId(), null, grandChild.getId(), null, null, null, null,
                null)).isInstanceOf(BizException.class).hasMessageContaining("不能是自身的下级");
        assertThat(roleService.require(parent.getId()).getParentId()).isZero();
        assertThat(roleService.collectSubtreeIds(parent.getId()))
                .containsExactlyInAnyOrder(parent.getId(), child.getId(), grandChild.getId());
        roleService.update(grandChild.getId(), null, 0L, null, null, null, null, null);
        assertThat(roleService.require(grandChild.getId()).getParentId()).isZero();
    }

    @Test
    void copyDuplicatesPermissionsAndCustomScope() {
        PermissionRecord read = permissionService.create("doc:read2", "读", "API", null, null, null);
        RoleRecord source = roleService.create("auditor", "审计", null, "CUSTOM", null, null, null);
        roleService.assignPermissions(source.getId(), List.of(read.getId()));
        RoleRecord copy = roleService.copy(source.getId(), "auditor-copy", "审计副本");
        assertThat(copy.getCode()).isEqualTo("auditor-copy");
        assertThat(roleService.permissionIdsOf(copy.getId())).containsExactly(read.getId());
        assertThat(copy.getDataScope()).isEqualTo("CUSTOM");
    }

    @Test
    void dataScopeCustomStoresDeptIds() {
        cn.slainc.modules.upms.service.dept.DeptService deptService = applicationContext.getBean(DeptService.class);
        Long deptA = deptService.create("A 部", null, null, null).getId();
        Long deptB = deptService.create("B 部", null, null, null).getId();
        RoleRecord role = roleService.create("scoped", "范围", null, "SELF", null, null, null);
        roleService.setDataScope(role.getId(), "CUSTOM", List.of(deptA, deptB));
        assertThat(roleService.require(role.getId()).getDataScope()).isEqualTo("CUSTOM");
        assertThat(roleService.customDeptIdsOf(role.getId())).containsExactlyInAnyOrder(deptA, deptB);
    }

    @Test
    void disabledRoleExcludedFromResolution() {
        PermissionRecord read = permissionService.create("doc:read3", "读", "API", null, null, null);
        RoleRecord role = roleService.create("suspended", "停用", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), List.of(read.getId()));
        roleService.update(role.getId(), null, null, null, null, "disabled", null, null);
        UserRecord user = userService.create("olga", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        userService.assignRoles(user.getId(), List.of(role.getId()));
        assertThat(roleService.resolveRoleCodes(user.getId())).isEmpty();
        assertThat(roleService.resolvePermissionCodes(user.getId())).isEmpty();
    }

    @Test
    void deleteDetachesChildrenAndClearsBindings() {
        RoleRecord parent = roleService.create("p1", "父", null, null, null, null, null);
        RoleRecord child = roleService.create("c1", "子", parent.getId(), null, null, null, null);
        roleService.delete(parent.getId());
        assertThat(roleService.require(child.getId()).getParentId()).isEqualTo(0L);
    }

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
}
