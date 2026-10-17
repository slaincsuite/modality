package cn.slainc.modules.upms.service.user;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.spi.UserUsage;
import cn.slainc.core.spi.UserUsageProvider;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.UpmsKeys;
import cn.slainc.modules.upms.dto.ActiveUserView;
import cn.slainc.modules.upms.entity.UserGroupRecord;
import cn.slainc.modules.upms.entity.UserOperationLogRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.mapper.UserOperationLogMapper;
import cn.slainc.modules.upms.service.group.UserGroupService;
import cn.slainc.modules.upms.web.self.CurrentUserAccessController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserUsageAndStatsTest extends UpmsIntegrationTestBase {

    @Autowired
    private UserService userService;

    @Autowired
    private UserUsageProvider userUsageProvider;

    @Autowired
    private UserStatsService statsService;

    @Autowired
    private UserOperationLogMapper operationLogMapper;

    @Autowired
    private UserGroupService groupService;

    @Autowired
    private CurrentUserAccessController currentUserAccessController;

    @Test
    void usageProviderCountsActiveAndUnavailableUsers() {
        UserRecord active = userService.create("usage-active", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        UserRecord disabled = userService.create("usage-disabled", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        userService.create("usage-second", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        userService.changeStatus(disabled.getId(), UpmsKeys.STATUS_DISABLED);

        UserUsage usage = userUsageProvider.usage(1L);
        assertThat(usage.total()).isEqualTo(3);
        assertThat(usage.active()).isEqualTo(2);
        assertThat(usage.disabled()).isEqualTo(1);
        assertThat(active.getStatus()).isEqualTo(UpmsKeys.STATUS_ENABLED);
    }

    @Test
    void activeUsersAggregateFromOperationLogsAndOverviewCarriesExtraFields() {
        UserRecord first = userService.create("stats-first", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        UserRecord second = userService.create("stats-second", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        long now = System.currentTimeMillis();
        operationLogMapper.insert(log(first.getId(), now - 1000));
        operationLogMapper.insert(log(first.getId(), now - 500));
        operationLogMapper.insert(log(second.getId(), now - 200));

        List<ActiveUserView> active = statsService.activeUsers(30);
        assertThat(active).hasSize(2);
        assertThat(active.get(0).userId()).isEqualTo(second.getId());
        assertThat(active.get(1).operationCount()).isEqualTo(2);
        assertThat(statsService.activeUsers(0)).hasSize(2);

        Map<String, Object> overview = statsService.overview();
        assertThat(overview).containsKeys("idpCount", "highRiskPolicyCount", "sensitiveOperationCount");
        assertThat(overview.get("idpCount")).isEqualTo(0L);
    }

    @Test
    void currentUserEndpointsUseContextPermissionsAndGroups() {
        UserRecord user = userService.create("current-user", null, null, null, null, null,
                LoginType.TENANT_USER, 1L, "admin");
        UserGroupRecord group = groupService.create("当前用户组", null);
        groupService.assignMembers(group.getId(), List.of(user.getId()));
        UserContext.set(new UserContext.UserInfo(user.getId(), user.getUsername(), LoginType.TENANT_USER,
                Set.of("role:demo"), Set.of("auth:me:read"), false));

        assertThat(currentUserAccessController.permissions()).containsExactly("auth:me:read");
        assertThat(currentUserAccessController.groups()).extracting(UserGroupRecord::getName)
                .containsExactly("当前用户组");
    }

    private UserOperationLogRecord log(Long userId, long createTime) {
        UserOperationLogRecord record = new UserOperationLogRecord();
        record.setTenantId(1L);
        record.setUserId(userId);
        record.setUsername("stats-user-" + userId);
        record.setModuleName("upms");
        record.setOperation("UPDATE");
        record.setSuccess(true);
        record.setCostMillis(1L);
        record.setCreateTime(createTime);
        return record;
    }
}
