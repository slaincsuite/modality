package cn.slainc.modules.upms.service;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.entity.UserRoleRecord;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.mapper.UserRoleMapper;
import cn.slainc.policy.admin.PolicyAdminService;
import cn.slainc.policy.admin.PolicyBindingService;
import cn.slainc.policy.engine.PolicyEngine;
import cn.slainc.policy.model.Effect;
import cn.slainc.policy.model.PolicyBinding;
import cn.slainc.policy.model.PolicyDocument;
import cn.slainc.policy.model.PolicyRequest;
import cn.slainc.policy.model.PolicyStatement;
import cn.slainc.policy.model.PolicySubjectType;
import cn.slainc.security.kick.KickService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class UserServiceTest extends UpmsIntegrationTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserGroupService userGroupService;

    @Autowired
    private PolicyAdminService policyAdminService;

    @Autowired
    private PolicyBindingService policyBindingService;

    @Autowired
    private PolicyEngine policyEngine;

    @MockitoSpyBean
    private KickService kickService;

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
    void softDeleteReleasesUsernameForReuse() {
        UserRecord user = userService.create("reuse-me", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        userService.delete(user.getId());
        assertThat(userMapper.selectById(user.getId()).getUsername()).isEqualTo("reuse-me#deleted#" + user.getId());

        UserRecord rebuilt = userService.create("reuse-me", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        assertThat(rebuilt.getId()).isNotEqualTo(user.getId());

        String longName = "n".repeat(64);
        UserRecord longUser = userService.create(longName, null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        userService.delete(longUser.getId());
        String released = userMapper.selectById(longUser.getId()).getUsername();
        assertThat(released).hasSizeLessThanOrEqualTo(64).endsWith("#deleted#" + longUser.getId());
        assertThat(userService.create(longName, null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin"))
                .isNotNull();
    }

    @Test
    void searchFiltersByRoleAndGroup() {
        RoleRecord role = roleService.create("search-role", "检索角色", null, null, null, null, null);
        UserGroupRecord group = userGroupService.create("检索组", null);
        UserRecord bound = userService.create("search-bound", null, null, null, null, null, LoginType.TENANT_USER,
                1L, "admin");
        userService.create("search-free", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        userService.assignRoles(bound.getId(), List.of(role.getId()));
        userGroupService.assignMembers(group.getId(), List.of(bound.getId()));

        PageResult<UserRecord> byRole = userService.search(null, null, null, null, null, null, role.getId(), null,
                null, null, null, 1, 10);
        assertThat(byRole.total()).isEqualTo(1);
        assertThat(byRole.records().get(0).getUsername()).isEqualTo("search-bound");
        assertThat(userService.search(null, null, null, null, null, null, null, group.getId(), null, null, null, 1, 10)
                .total()).isEqualTo(1);
        assertThat(userService.search(null, null, null, null, null, null, 404040L, null, null, null, null, 1, 10)
                .total()).isZero();
        assertThat(userService.search(null, null, null, null, null, null, null, null, null, null, null, 1, 10)
                .total()).isEqualTo(2);
    }

    @Test
    void mergeMigratesPolicyBindingsToTarget() {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid("allow-report");
        statement.setEffect(Effect.ALLOW.name());
        statement.setActions(List.of("report:*"));
        statement.setResources(List.of("finance:quarterly"));
        PolicyDocument document = new PolicyDocument();
        document.setStatements(List.of(statement));
        String policyId = policyAdminService.create("报表查看", PolicySubjectType.ROLE, "nobody", null, document,
                "测试种子策略", "tester").getId();

        UserRecord source = userService.create("policy-src", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        UserRecord target = userService.create("policy-dst", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        PolicyBinding binding = policyBindingService.bindToUser(policyId, source.getId(), LoginType.TENANT_USER,
                "tester");

        userService.merge(source.getId(), target.getId());

        List<PolicyBinding> targetBindings = policyBindingService.query(PolicyBinding.SUBJECT_USER,
                String.valueOf(target.getId()), null);
        assertThat(targetBindings).hasSize(1);
        assertThat(targetBindings.get(0).getPolicyId()).isNotEqualTo(binding.getPolicyId());
        assertThat(policyBindingService.query(PolicyBinding.SUBJECT_USER, String.valueOf(source.getId()), null))
                .isEmpty();

        policyEngine.invalidate();
        PolicyRequest request = PolicyRequest.of("report:read", "finance:quarterly")
                .userId(String.valueOf(target.getId())).loginType(LoginType.TENANT_USER.value());
        assertThat(policyEngine.evaluate(request).allowed()).isTrue();
        PolicyRequest sourceRequest = PolicyRequest.of("report:read", "finance:quarterly")
                .userId(String.valueOf(source.getId())).loginType(LoginType.TENANT_USER.value());
        assertThat(policyEngine.evaluate(sourceRequest).allowed()).isFalse();
    }

    @Test
    void disablingOrLockingUserKicksExistingSessions() {
        doNothing().when(kickService).kickout(any(LoginType.class), any(), anyString());
        UserRecord user = userService.create("kick-me", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");

        userService.changeStatus(user.getId(), UpmsKeys.STATUS_DISABLED);
        verify(kickService).kickout(LoginType.TENANT_USER, user.getId(), "账号已被禁用");

        userService.changeStatus(user.getId(), UpmsKeys.STATUS_LOCKED);
        verify(kickService).kickout(LoginType.TENANT_USER, user.getId(), "账号已被锁定");

        userService.changeStatus(user.getId(), UpmsKeys.STATUS_ENABLED);
        verify(kickService, times(1)).kickout(LoginType.TENANT_USER, user.getId(), "账号已被禁用");
        verify(kickService, times(1)).kickout(LoginType.TENANT_USER, user.getId(), "账号已被锁定");
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
