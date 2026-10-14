package cn.slainc.modules.upms.log;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.LoginEvent;
import cn.slainc.modules.upms.UpmsIntegrationTestBase;
import cn.slainc.modules.upms.entity.RoleRecord;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.RoleService;
import cn.slainc.modules.upms.service.UserService;
import cn.slainc.modules.upms.service.UserStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogAndStatsTest extends UpmsIntegrationTestBase {

    @Autowired
    private LoginRecordService loginRecordService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private OperationLogStore operationLogStore;

    @Autowired
    private UserService userService;

    @Autowired
    private UserStatsService statsService;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private RoleService roleService;

    @Test
    void loginEventsCollectedWithDeviceParsing() {
        UserRecord user = userService.create("logger", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        publisher.publishEvent(LoginEvent.success(user.getId(), "logger", LoginType.TENANT_USER, "10.1.1.1"));
        publisher.publishEvent(cn.slainc.core.event.LoginEvent.failure("logger", LoginType.TENANT_USER, "10.1.1.2",
                "用户名或密码错误"));
        List<cn.slainc.modules.upms.entity.UserLoginRecordRecord> records = loginRecordService.list(null, "logger",
                null, null, null, null, null, 10);
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getSucceeded()).isFalse();
        assertThat(records.get(0).getFailureReason()).contains("密码错误");
        assertThat(records.get(1).getSucceeded()).isTrue();
        assertThat(records.get(1).getClientIp()).isEqualTo("10.1.1.1");
    }

    @Test
    void deviceParserCategorizesAgents() {
        assertThat(DeviceParser.parse("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)")).isEqualTo("mobile");
        assertThat(DeviceParser.parse("Mozilla/5.0 (iPad; CPU OS 16_0)")).isEqualTo("tablet");
        assertThat(DeviceParser.parse("Mozilla/5.0 (Windows NT 10.0; Win64)")).isEqualTo("pc");
        assertThat(DeviceParser.parse("")).isEqualTo("unknown");
    }

    @Test
    void operationLogStorePersistsAuditRecord() {
        operationLogStore.save(new cn.slainc.logger.oplog.AuditRecord("aid-1",
                java.time.Instant.now(), "创建用户", "upms",
                cn.slainc.logger.oplog.AuditOperation.CREATE, cn.slainc.logger.oplog.AuditCategory.DATA,
                cn.slainc.logger.oplog.AuditLevel.INFO, true, 9L, "9", "op-user", 1L, "10.2.2.2", "UA", "dev-1",
                "/upms/user", "POST", null, null, null, "UserRecord", "{\"name\":\"x\"}", "ok", null, null, null,
                null, 12L));
        var page = operationLogService.search(null, "op-user", "upms", null, true, null, null, 1, 10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).getAuditId()).isEqualTo("aid-1");
        assertThat(page.records().get(0).getDetail()).contains("target=UserRecord");
        assertThat(operationLogService.search(null, null, null, null, false, null, null, 1, 10).total())
                .isZero();
    }

    @Test
    void statsOverviewTrendAndRoleDistribution() {
        UserRecord active = userService.create("stat-active", null, null, null, null, null, LoginType.TENANT_USER,
                1L, "admin");
        userService.create("stat-idle", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        publisher.publishEvent(LoginEvent.success(active.getId(), "stat-active", LoginType.TENANT_USER, "10.3.3.3"));

        Map<String, Object> overview = statsService.overview();
        assertThat((Long) overview.get("totalUsers")).isEqualTo(2);
        assertThat((Long) overview.get("dau")).isEqualTo(1);
        assertThat((Long) overview.get("mau")).isEqualTo(1);

        List<Map<String, Object>> trend = statsService.newUserTrend(7);
        assertThat(trend).hasSize(7);
        assertThat((Long) trend.get(trend.size() - 1).get("count")).isEqualTo(2);

        assertThat(statsService.roleDistribution()).extracting(row -> row.get("role"))
                .containsExactly("未分配角色");
    }

    @Test
    void roleDistributionAggregatesBindingsByRole() {
        RoleRecord role = roleService.create("stats-role", "统计角色", null, null, null, null, null);
        UserRecord bound = userService.create("stats-bound", null, null, null, null, null, LoginType.TENANT_USER, 1L,
                "admin");
        userService.create("stats-free", null, null, null, null, null, LoginType.TENANT_USER, 1L, "admin");
        userService.assignRoles(bound.getId(), List.of(role.getId()));

        List<Map<String, Object>> distribution = statsService.roleDistribution();
        assertThat(distribution).anySatisfy(row -> {
            assertThat(row).containsEntry("role", "统计角色");
            assertThat(row).containsEntry("count", 1);
        });
        assertThat(distribution).anySatisfy(row -> {
            assertThat(row).containsEntry("role", "未分配角色");
            assertThat(row).containsEntry("count", 1);
        });
        assertThat(distribution).noneSatisfy(row -> assertThat(row.get("count")).isEqualTo(0));
    }

    @Test
    void topIpsAndDevicesAggregated() {
        publisher.publishEvent(LoginEvent.success(null, "ip-user", LoginType.TENANT_USER, "10.8.8.1"));
        publisher.publishEvent(LoginEvent.success(null, "ip-user", LoginType.TENANT_USER, "10.8.8.1"));
        publisher.publishEvent(LoginEvent.success(null, "ip-user", LoginType.TENANT_USER, "10.8.8.2"));
        List<Map<String, Object>> ips = loginRecordService.topIps(5);
        assertThat(ips).isNotEmpty();
        assertThat(ips.get(0)).containsEntry("clientIp", "10.8.8.1");
        assertThat((Integer) ips.get(0).get("count")).isEqualTo(2);
        assertThat(loginRecordService.topDevices(5)).isNotEmpty();
    }
}
