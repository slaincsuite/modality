package cn.slainc.modules.upms.service.permission;

import cn.slainc.modules.upms.service.menu.MenuService;
import cn.slainc.modules.upms.service.role.RoleService;
import cn.slainc.modules.upms.service.user.UserService;

import cn.slainc.core.exception.BizException;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.MenuRecord;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.core.enums.LoginType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionMenuServiceTest extends UpmsIntegrationTestBase {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private MenuService menuService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Test
    void permissionTreeAndDeleteGuard() {
        PermissionRecord parent = permissionService.create("doc", "文档", "MENU", null, null, null);
        permissionService.create("doc:read", "读", "BUTTON", parent.getId(), null, null);
        assertThatThrownBy(() -> permissionService.delete(parent.getId()))
                .isInstanceOf(BizException.class).hasMessageContaining("下级");
        assertThat(permissionService.search("doc", null)).hasSize(2);
        assertThat(permissionService.tree()).extracting(PermissionRecord::getCode).containsExactly("doc");
    }

    @Test
    void permissionSearchByKeywordOrType() {
        permissionService.create("upms:user:create", "建用户", "API", null, null, null);
        permissionService.create("upms:role:create", "建角色", "API", null, null, null);
        permissionService.create("目录一", "目录一", "MENU", null, null, null);
        assertThat(permissionService.search("建用户", null)).hasSize(1);
        assertThat(permissionService.search(null, "MENU")).hasSize(1);
    }

    @Test
    void menuTreeKeepsAncestorsOfPermittedLeaf() {
        MenuRecord root = menuService.create("系统管理", null, MenuRecord.TYPE_DIRECTORY, "/admin", null, "gear",
                null, true, 1);
        MenuRecord page = menuService.create("用户管理", root.getId(), MenuRecord.TYPE_MENU, "/admin/user", null,
                null, "upms:user:query", true, 1);
        menuService.create("新建用户", page.getId(), MenuRecord.TYPE_BUTTON, null, null, null,
                "upms:user:create", true, 1);
        menuService.create("隐藏页", root.getId(), MenuRecord.TYPE_MENU, "/admin/hidden", null, null,
                "upms:hidden:view", false, 2);

        List<MenuRecord> granted = menuService.userMenuTree(Set.of("upms:user:query"));
        assertThat(granted).extracting(MenuRecord::getName).containsExactly("系统管理");
        List<MenuRecord> full = menuService.userMenuTree(Set.of("upms:user:query", "upms:user:create"));
        assertThat(full).extracting(MenuRecord::getName).contains("系统管理");
        List<MenuRecord> nothing = menuService.userMenuTree(Set.of("upms:none"));
        assertThat(nothing).isEmpty();
    }

    @Test
    void menuWithoutPermissionCodeVisibleToAll() {
        menuService.create("门户", null, MenuRecord.TYPE_MENU, "/portal", null, null, null, true, 1);
        assertThat(menuService.userMenuTree(Set.of())).hasSize(1);
    }

    @Test
    void menuSelfParentRejected() {
        MenuRecord menu = menuService.create("环", null, MenuRecord.TYPE_MENU, null, null, null, null, true, 1);
        assertThatThrownBy(() -> menuService.update(menu.getId(), null, menu.getId(), null, null, null, null, null,
                null, null, null)).isInstanceOf(BizException.class).hasMessageContaining("不能是自身");
    }

    @Test
    void permissionAndMenuDescendantParentRejected() {
        PermissionRecord permParent = permissionService.create("cyc", "环", "MENU", null, null, null);
        PermissionRecord permChild = permissionService.create("cyc:child", "环子", "BUTTON", permParent.getId(), null,
                null);
        assertThatThrownBy(() -> permissionService.update(permParent.getId(), null, null, permChild.getId(), null, null,
                null)).isInstanceOf(BizException.class).hasMessageContaining("不能是自身的下级");
        assertThat(permissionService.require(permParent.getId()).getParentId()).isZero();

        MenuRecord menuParent = menuService.create("环目录", null, MenuRecord.TYPE_DIRECTORY, null, null, null, null,
                true, 1);
        MenuRecord menuChild = menuService.create("环页面", menuParent.getId(), MenuRecord.TYPE_MENU, null, null, null,
                null, true, 1);
        assertThatThrownBy(() -> menuService.update(menuParent.getId(), null, menuChild.getId(), null, null, null, null,
                null, null, null, null)).isInstanceOf(BizException.class).hasMessageContaining("不能是自身的下级");
        assertThat(menuService.collectSubtreeIds(menuParent.getId()))
                .containsExactlyInAnyOrder(menuParent.getId(), menuChild.getId());
    }

    @Test
    void userMenuReflectsRolePermissions() {
        PermissionRecord perm = permissionService.create("menu:demo", "演示", "MENU", null, null, null);
        RoleRecord role = roleService.create("viewer", "观察员", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), List.of(perm.getId()));
        UserRecord user = userService.create("pat", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        userService.assignRoles(user.getId(), List.of(role.getId()));
        menuService.create("演示页", null, MenuRecord.TYPE_MENU, "/demo", null, null, "menu:demo", true, 1);
        assertThat(menuService.userMenuTree(Set.copyOf(roleService.resolvePermissionCodes(user.getId()))))
                .hasSize(1);
    }
}
