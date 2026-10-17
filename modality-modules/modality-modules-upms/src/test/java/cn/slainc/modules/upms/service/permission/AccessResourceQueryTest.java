package cn.slainc.modules.upms.service.permission;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.dto.GroupMemberBindingView;
import cn.slainc.modules.upms.dto.UserGroupBindingView;
import cn.slainc.modules.upms.entity.DeptRecord;
import cn.slainc.modules.upms.entity.MenuRecord;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.dept.DeptService;
import cn.slainc.modules.upms.service.group.UserGroupService;
import cn.slainc.modules.upms.service.menu.MenuService;
import cn.slainc.modules.upms.service.role.RoleService;
import cn.slainc.modules.upms.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccessResourceQueryTest extends UpmsIntegrationTestBase {

    @Autowired
    private DeptService deptService;

    @Autowired
    private MenuService menuService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserGroupService groupService;

    @Autowired
    private UserService userService;

    @Test
    void departmentTreeAndRoleReverseLookup() {
        DeptRecord root = deptService.create("总部", null, null, 1);
        DeptRecord child = deptService.create("研发", root.getId(), null, 1);
        DeptRecord leaf = deptService.create("平台组", child.getId(), null, 1);
        RoleRecord role = roleService.create("dept-role", "部门角色", null, "CUSTOM", null, null, null);
        roleService.setDataScope(role.getId(), "CUSTOM", List.of(leaf.getId()));

        DeptRecord treeRoot = deptService.tree().get(0);
        assertThat(treeRoot.getChildren()).extracting(DeptRecord::getName).containsExactly("研发");
        assertThat(treeRoot.getChildren().get(0).getChildren()).extracting(DeptRecord::getName)
                .containsExactly("平台组");
        assertThat(deptService.require(child.getId()).getName()).isEqualTo("研发");
        assertThat(roleService.rolesOfDept(leaf.getId())).extracting(RoleRecord::getCode)
                .containsExactly("dept-role");
    }

    @Test
    void menuAndPermissionTreesRecurseAndKeepFlatCompatibility() {
        PermissionRecord permissionRoot = permissionService.create("resource:root", "资源根", "MENU", null, null, null);
        permissionService.create("resource:read", "资源读", "BUTTON", permissionRoot.getId(), null, null);
        PermissionRecord permissionTreeRoot = permissionService.tree().get(0);
        assertThat(permissionTreeRoot.getChildren()).extracting(PermissionRecord::getCode)
                .containsExactly("resource:read");

        MenuRecord menuRoot = menuService.create("资源管理", null, MenuRecord.TYPE_DIRECTORY, "/resource", null,
                null, null, true, 1);
        MenuRecord page = menuService.create("资源页面", menuRoot.getId(), MenuRecord.TYPE_MENU, "/resource/page",
                null, null, "resource:read", true, 1);
        menuService.create("资源按钮", page.getId(), MenuRecord.TYPE_BUTTON, null, null, null,
                "resource:read", true, 1);
        MenuRecord menuTreeRoot = menuService.tree().get(0);
        assertThat(menuTreeRoot.getChildren()).extracting(MenuRecord::getName).containsExactly("资源页面");
        assertThat(menuService.list()).hasSize(3);
        assertThat(menuService.userMenuTree(Set.of("resource:read"))).hasSize(1);
        assertThat(menuService.userMenuTree(Set.of("resource:read")).get(0).getChildren()).hasSize(1);
    }

    @Test
    void permissionPageAndReverseMenuRoleLookups() {
        PermissionRecord permission = permissionService.create("reverse:read", "反向读", "API", null, null, null);
        PermissionRecord second = permissionService.create("reverse:write", "反向写", "API", null, null, null);
        RoleRecord role = roleService.create("reverse-role", "反向角色", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), List.of(permission.getId()));
        menuService.create("反向页面", null, MenuRecord.TYPE_MENU, "/reverse", null, null, "reverse:read", true, 1);

        PageResult<PermissionRecord> page = permissionService.page("reverse", "API", 1, 1);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).hasSize(1);
        assertThat(permissionService.require(permission.getId()).getCode()).isEqualTo("reverse:read");
        assertThat(permissionService.rolesOf(permission.getId())).extracting(RoleRecord::getCode)
                .containsExactly("reverse-role");
        assertThat(permissionService.menusOf(permission.getId())).extracting(MenuRecord::getName)
                .containsExactly("反向页面");
        assertThat(permissionService.rolesOfCodes(List.of("reverse:read"))).extracting(RoleRecord::getCode)
                .containsExactly("reverse-role");
        assertThat(permissionService.rolesOf(second.getId())).isEmpty();
    }

    @Test
    void groupMemberAndUserGroupBindingsExposeJoinTime() {
        UserGroupRecord group = groupService.create("绑定时间组", null);
        UserRecord user = userService.create("binding-time-user", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        groupService.assignMembers(group.getId(), List.of(user.getId()));

        List<GroupMemberBindingView> members = groupService.memberBindingsOf(group.getId());
        assertThat(members).hasSize(1);
        assertThat(members.get(0).user().getUsername()).isEqualTo("binding-time-user");
        assertThat(members.get(0).joinedAt()).isNotNull();

        List<UserGroupBindingView> groups = groupService.groupBindingsOf(user.getId());
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).group().getName()).isEqualTo("绑定时间组");
        assertThat(groups.get(0).joinedAt()).isNotNull();
    }
}
