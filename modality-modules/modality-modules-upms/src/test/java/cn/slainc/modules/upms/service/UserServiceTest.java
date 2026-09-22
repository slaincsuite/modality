package cn.slainc.modules.upms.service;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.entity.UserRoleRecord;
import cn.slainc.modules.upms.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest extends UpmsIntegrationTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Test
    void createUserWithDefaultPasswordForcesChange() {
        UserRecord user = userService.create("alice", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        assertThat(user.getId()).isNotNull();
        assertThat(user.getMustChangePassword()).isTrue();
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(user.getStatus()).isEqualTo(UpmsKeys.STATUS_ENABLED);
    }

    @Test
    void duplicateUsernameRejectedPerLoginType() {
        userService.create("bob", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        assertThatThrownBy(() -> userService.create("bob", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin")).isInstanceOf(BizException.class).hasMessageContaining("已存在");
        userService.create("bob", null, null, null, null, null, LoginType.PLATFORM_USER, 1L, "admin");
    }

    @Test
    void statusChangeValidatedAndApplied() {
        UserRecord user = userService.create("carl", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        userService.changeStatus(user.getId(), UpmsKeys.STATUS_DISABLED);
        assertThat(userService.get(user.getId()).getStatus()).isEqualTo(UpmsKeys.STATUS_DISABLED);
        assertThatThrownBy(() -> userService.changeStatus(user.getId(), "frozen"))
                .isInstanceOf(BizException.class).hasMessageContaining("非法的用户状态");
    }

    @Test
    void softDeleteRemovesRoleBindingsAndBlocksReuse() {
        UserRecord user = userService.create("dave", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        userService.assignRoles(user.getId(), List.of(1L));
        userService.delete(user.getId());
        assertThat(userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleRecord>()
                .eq(UserRoleRecord::getUserId, user.getId()))).isZero();
        assertThatThrownBy(() -> userService.get(user.getId())).isInstanceOf(BizException.class)
                .hasMessageContaining("不存在或已注销");
    }

    @Test
    void searchByMultipleConditions() {
        userService.create("eve", "伊芙", null, "eve@example.com", "13800008888", null, LoginType.TENANT_USER, 1L,
                "admin");
        userService.create("frank", "弗兰克", null, "frank@example.com", null, null, LoginType.TENANT_USER, 1L,
                "admin");
        PageResult<UserRecord> byPhoneTail = userService.search(null, null, null, null, null, null, null, null,
                LoginType.TENANT_USER.value(), null, null, 1, 10);
        assertThat(byPhoneTail.total()).isEqualTo(2);
        assertThat(userService.search("eve", null, null, null, null, null, null, null, null, null, null, 1, 10)
                .total()).isEqualTo(1);
        assertThat(userService.search(null, null, null, null, UpmsKeys.STATUS_DISABLED, null, null, null, null, null,
                null, 1, 10).total()).isZero();
    }

    @Test
    void mergeMovesBindingsAndSoftDeletesSource() {
        UserRecord source = userService.create("gina", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        UserRecord target = userService.create("hank", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        userService.assignRoles(source.getId(), List.of(9L));
        userService.merge(source.getId(), target.getId());
        assertThat(userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleRecord>()
                .eq(UserRoleRecord::getUserId, target.getId()).eq(UserRoleRecord::getRoleId, 9L))).isEqualTo(1);
        assertThat(userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleRecord>()
                .eq(UserRoleRecord::getUserId, source.getId()))).isZero();
        UserRecord merged = userService.get(target.getId());
        assertThat(merged.getUsername()).isEqualTo("hank");
        assertThatThrownBy(() -> userService.get(source.getId())).isInstanceOf(BizException.class);
    }

    @Test
    void mergeRejectsSameUserAndCrossLoginType() {
        UserRecord user = userService.create("ivy", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        UserRecord other = userService.create("jack", null, null, null, null, null, LoginType.PLATFORM_USER, 1L,
                "admin");
        assertThatThrownBy(() -> userService.merge(user.getId(), user.getId())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> userService.merge(user.getId(), other.getId()))
                .isInstanceOf(BizException.class).hasMessageContaining("跨账号体系");
    }

    @Test
    void batchDisableSkipsMissingAndDisabled() {
        UserRecord user = userService.create("kate", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        int disabled = userService.batchDisable(List.of(user.getId(), 9999L));
        assertThat(disabled).isEqualTo(1);
        assertThat(userService.batchDisable(List.of(user.getId()))).isZero();
    }

    @Test
    void loginRestrictionMatchersEnforceUserLevelRules() {
        UserRecord user = userService.create("lion", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        user.setAllowedIps("10.0.0.*,192.168.1.5");
        assertThat(userService.checkAllowedIp(user, "10.0.0.8")).isTrue();
        assertThat(userService.checkAllowedIp(user, "172.16.0.1")).isFalse();
        user.setAllowedTimeRange("00:00-23:59");
        long tenAm = java.time.LocalDate.now().atTime(10, 0).atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        assertThat(userService.checkAllowedTime(user, tenAm)).isTrue();
        assertThat(userService.checkAllowedTime(user, tenAm - 1)).isTrue();
        user.setAllowedDevices("pc");
        assertThat(userService.checkAllowedDevice(user, "pc")).isTrue();
        assertThat(userService.checkAllowedDevice(user, "mobile")).isFalse();
        assertThat(userService.validateRestrictions(user, "10.0.0.1", "mobile")).hasSize(1);
    }
}
