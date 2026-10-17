package cn.slainc.modules.upms.service.role;

import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.dto.RolePermissionSourceView;
import cn.slainc.modules.upms.dto.RoleUserBindingView;
import cn.slainc.modules.upms.entity.PermissionRecord;
import cn.slainc.modules.upms.entity.PolicyBindingRecord;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.PolicyBindingMapper;
import cn.slainc.modules.upms.service.group.UserGroupService;
import cn.slainc.modules.upms.service.permission.PermissionService;
import cn.slainc.modules.upms.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RoleBindingQueryTest extends UpmsIntegrationTestBase {

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserGroupService groupService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PolicyBindingMapper policyBindingMapper;

    @Test
    void rolePageAllowsMissingNameAndCode() {
        roleService.create("blank-search", "空条件检索", null, null, null, null, null);
        assertThatCode(() -> roleService.search(null, null, null, 1, 10)).doesNotThrowAnyException();
        assertThat(roleService.search("", "", null, 1, 10).total()).isEqualTo(1);
    }

    @Test
    void roleBindingsExposeTimesGroupsAndPermissionSources() {
        PermissionRecord permission = permissionService.create("role:source:read", "读来源", "API", null, null, null);
        RoleRecord role = roleService.create("source-reader", "来源角色", null, "CUSTOM", null, null, null);
        roleService.assignPermissions(role.getId(), List.of(permission.getId()));
        UserRecord user = userService.create("role-bound-user", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        roleService.assignUsers(role.getId(), List.of(user.getId()));
        UserGroupRecord group = groupService.create("来源用户组", null);
        roleService.assignGroups(role.getId(), List.of(group.getId()));

        PolicyBindingRecord policy = new PolicyBindingRecord();
        policy.setTenantId(1L);
        policy.setPolicyId("policy-source-1");
        policy.setSubjectType(PolicyBindingRecord.SUBJECT_ROLE);
        policy.setSubjectId(String.valueOf(role.getId()));
        policy.setOperator("tester");
        policy.setCreateTime(System.currentTimeMillis());
        policyBindingMapper.insert(policy);

        List<RoleUserBindingView> bindings = roleService.userBindingsOf(role.getId());
        assertThat(bindings).hasSize(1);
        assertThat(bindings.get(0).user().getUsername()).isEqualTo("role-bound-user");
        assertThat(bindings.get(0).boundAt()).isNotNull();
        assertThat(roleService.groupsOf(role.getId())).extracting(UserGroupRecord::getName)
                .containsExactly("来源用户组");
        RolePermissionSourceView sources = roleService.permissionSourcesOf(role.getId());
        assertThat(sources.directPermissions()).extracting(PermissionRecord::getCode)
                .containsExactly("role:source:read");
        assertThat(sources.policyBindings()).extracting(PolicyBindingRecord::getPolicyId)
                .containsExactly("policy-source-1");
    }

    @Test
    void groupRolesParticipateInEffectivePermissions() {
        PermissionRecord permission = permissionService.create("group:source:read", "组读", "API", null, null, null);
        RoleRecord role = roleService.create("group-source", "组来源", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), List.of(permission.getId()));
        UserGroupRecord group = groupService.create("权限组", null);
        groupService.assignRoles(group.getId(), List.of(role.getId()));
        UserRecord user = userService.create("group-permission-user", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        groupService.assignMembers(group.getId(), List.of(user.getId()));

        assertThat(groupService.rolesOf(group.getId())).extracting(RoleRecord::getCode)
                .containsExactly("group-source");
        assertThat(roleService.resolveRoleCodes(user.getId())).containsExactly("group-source");
        assertThat(roleService.resolvePermissionCodes(user.getId())).containsExactly("group:source:read");
    }
}
