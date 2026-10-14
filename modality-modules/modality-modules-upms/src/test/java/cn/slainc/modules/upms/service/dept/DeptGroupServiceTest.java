package cn.slainc.modules.upms.service.dept;

import cn.slainc.modules.upms.service.group.UserGroupService;
import cn.slainc.modules.upms.service.user.UserService;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.DeptRecord;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeptGroupServiceTest extends UpmsIntegrationTestBase {

    @Autowired
    private DeptService deptService;

    @Autowired
    private UserGroupService groupService;

    @Autowired
    private UserService userService;

    @Test
    void deptTreeAndSubtreeCollection() {
        DeptRecord hq = deptService.create("总部", null, null, 1);
        DeptRecord rd = deptService.create("研发部", hq.getId(), null, 1);
        DeptRecord frontend = deptService.create("前端组", rd.getId(), null, 1);
        assertThat(deptService.tree()).extracting(DeptRecord::getName).containsExactly("总部");
        assertThat(deptService.collectSubtreeIds(rd.getId()))
                .containsExactlyInAnyOrder(rd.getId(), frontend.getId());
    }

    @Test
    void deptDeleteBlockedByChildrenOrUsers() {
        DeptRecord hq = deptService.create("总部2", null, null, 1);
        deptService.create("运维部", hq.getId(), null, 1);
        assertThatThrownBy(() -> deptService.delete(hq.getId()))
                .isInstanceOf(BizException.class).hasMessageContaining("下级部门");

        DeptRecord empty = deptService.create("空部门", null, null, 1);
        userService.create("quinn", null, null, null, null, empty.getId(), LoginType.TENANT_USER, 1L, "admin");
        assertThatThrownBy(() -> deptService.delete(empty.getId()))
                .isInstanceOf(BizException.class).hasMessageContaining("迁移用户");
    }

    @Test
    void deptUpdateRejectsSubtreeParent() {
        DeptRecord hq = deptService.create("总部3", null, null, 1);
        DeptRecord child = deptService.create("子部门", hq.getId(), null, 1);
        assertThatThrownBy(() -> deptService.update(hq.getId(), null, child.getId(), null, null, null))
                .isInstanceOf(BizException.class).hasMessageContaining("不能是自身的下级");
    }

    @Test
    void usersOfDeptIncludingChildren() {
        DeptRecord hq = deptService.create("总部4", null, null, 1);
        DeptRecord rd = deptService.create("研发部4", hq.getId(), null, 1);
        UserRecord hqUser = userService.create("rita", null, null, null, null, hq.getId(), LoginType.TENANT_USER,
                1L, "admin");
        userService.create("sam", null, null, null, null, rd.getId(), LoginType.TENANT_USER, 1L, "admin");
        PageResult<UserRecord> direct = deptService.usersOf(hq.getId(), false, 1, 10);
        assertThat(direct.records()).extracting(UserRecord::getUsername).containsExactly("rita");
        PageResult<UserRecord> all = deptService.usersOf(hq.getId(), true, 1, 10);
        assertThat(all.total()).isEqualTo(2);
        assertThat(hqUser.getDeptId()).isEqualTo(hq.getId());
    }

    @Test
    void groupMemberLifecycle() {
        UserGroupRecord group = groupService.create("高权限组", "测试组");
        UserRecord user = userService.create("tina", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        groupService.assignMembers(group.getId(), List.of(user.getId()));
        assertThat(groupService.memberIdsOf(group.getId())).containsExactly(user.getId());
        assertThat(groupService.groupsOf(user.getId())).extracting(UserGroupRecord::getName)
                .containsExactly("高权限组");
        groupService.assignMembers(group.getId(), List.of());
        assertThat(groupService.memberIdsOf(group.getId())).isEmpty();
    }

    @Test
    void groupDeleteClearsMembers() {
        UserGroupRecord group = groupService.create("临时组", null);
        UserRecord user = userService.create("victor", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        groupService.assignMembers(group.getId(), List.of(user.getId()));
        groupService.delete(group.getId());
        assertThat(groupService.groupsOf(user.getId())).isEmpty();
        assertThatThrownBy(() -> groupService.require(group.getId())).isInstanceOf(BizException.class);
    }
}
