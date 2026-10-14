package cn.slainc.boot;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.upms.mapper.UserMapper;
import cn.slainc.modules.upms.security.UpmsAccountAuthProvider;
import cn.slainc.modules.upms.service.permission.PermissionService;
import cn.slainc.modules.upms.service.role.RoleService;
import cn.slainc.modules.upms.service.user.UserService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 批次 15 整机验收：工单中心 HTTP 全链路（创建 → 订阅 → 分派 → 通知 → 挂起恢复 → 催办转派 → 解决关闭重开 → 统计导出），含 401/403/400 负向对照
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class Batch15WorkOrderAcceptanceTest {

    private static final String PASSWORD = "Password#123";

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private WorkOrderNotificationStore notificationStore;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private AcceptanceHttpSupport http;

    // 验收用 t_user 真实账号走完整登录链路，@Primary 压过 Batch4 基座的内存账号提供者
    @TestConfiguration(proxyBeanMethods = false)
    static class DbAccountConfiguration {

        @Bean
        @Primary
        public AccountAuthProvider batch15DbAccountProvider(UserMapper userMapper) {
            return new UpmsAccountAuthProvider(userMapper);
        }
    }

    @BeforeEach
    void setUp() {
        http = new AcceptanceHttpSupport(port);
        TenantContext.set(1L);
        for (String prefix : new String[]{"satoken:", "modality:security:perm:", "modality:workorder:"}) {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        for (String table : new String[]{"t_work_order_attachment", "t_work_order_activity",
                "t_work_order_reply", "t_work_order", "t_user_login_record", "t_user_operation_log",
                "t_role_permission", "t_user_role", "t_permission", "t_role", "t_user"}) {
            try {
                jdbc.execute("DELETE FROM " + table);
            } catch (org.springframework.dao.DataAccessException ignored) {
                // 懒建表，首个用例前可能不存在
            }
        }
    }

    private String grantOperator() throws Exception {
        var query = permissionService.create("workorder:order:query", "查工单", "API", null, null, null);
        var assign = permissionService.create("workorder:order:assign", "处理工单", "API", null, null, null);
        var close = permissionService.create("workorder:order:close", "关闭工单", "API", null, null, null);
        var stats = permissionService.create("workorder:stats:query", "查统计", "API", null, null, null);
        var export = permissionService.create("workorder:order:export", "导出工单", "API", null, null, null);
        var commentManage = permissionService.create("workorder:comment:manage", "管理评论", "API", null, null, null);
        var attachmentManage = permissionService.create("workorder:attachment:manage", "管理附件", "API", null, null, null);
        var notificationManage = permissionService.create("workorder:notification:manage", "管理通知", "API", null, null, null);
        var role = roleService.create("batch15-op", "工单操作员", null, null, null, null, null);
        roleService.assignPermissions(role.getId(), List.of(query.getId(), assign.getId(), close.getId(),
                stats.getId(), export.getId(), commentManage.getId(), attachmentManage.getId(),
                notificationManage.getId()));
        var operator = userService.create("batch15-op-user", null, PASSWORD, null, null, null,
                LoginType.TENANT_USER, 1L, "batch15");
        userService.assignRoles(operator.getId(), List.of(role.getId()));
        return http.login("batch15-op-user", PASSWORD, "pc-op");
    }

    private long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private Long createUserAndGetId(String username) {
        return userService.create(username, null, PASSWORD, null, null, null, LoginType.TENANT_USER, 1L,
                "batch15").getId();
    }

    private String loginUser(String username) throws Exception {
        return http.login(username, PASSWORD, "pc-" + username);
    }

    @Test
    void workOrderLifecycleOverHttpWithNotificationAndStats() throws Exception {
        String operatorToken = grantOperator();
        createUserAndGetId("batch15-user");
        String userToken = loginUser("batch15-user");

        var anonymous = http.send("POST", "/workorder/my", null,
                "{\"title\":\"匿名\",\"content\":\"匿名\"}");
        assertThat(anonymous.statusCode()).as(anonymous.body()).isEqualTo(401);

        var created = http.send("POST", "/workorder/my", userToken,
                "{\"title\":\"打印机故障\",\"content\":\"卡纸严重\",\"priority\":\"HIGH\"}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        assertThat((String) JsonPath.<String>read(created.body(), "$.data.number")).startsWith("WO");
        assertThat((String) JsonPath.<String>read(created.body(), "$.data.status")).isEqualTo("OPEN");
        long orderId = longOf(JsonPath.read(created.body(), "$.data.id"));

        http.send("POST", "/workorder/my/" + orderId + "/subscription", userToken, null);

        var forbidden = http.send("POST", "/workorder/manage/" + orderId + "/assign", userToken,
                "{\"assigneeId\":202}");
        assertThat(forbidden.statusCode()).as(forbidden.body()).isEqualTo(403);
        assertThat(forbidden.body()).contains("403000");

        var assigned = http.send("POST", "/workorder/manage/" + orderId + "/assign", operatorToken,
                "{\"assigneeId\":202}");
        assertThat(assigned.statusCode()).as(assigned.body()).isEqualTo(200);
        var detail = http.send("GET", "/workorder/manage/" + orderId, operatorToken, null);
        assertThat((String) JsonPath.<String>read(detail.body(), "$.data.status")).isEqualTo("IN_PROGRESS");
        assertThat(longOf(JsonPath.read(detail.body(), "$.data.assigneeId"))).isEqualTo(202L);

        http.send("POST", "/workorder/manage/" + orderId + "/resolve", operatorToken, null);
        http.send("POST", "/workorder/manage/" + orderId + "/close", operatorToken, null);
        assertThat((String) JsonPath.<String>read(
                http.send("GET", "/workorder/manage/" + orderId, operatorToken, null).body(),
                "$.data.status")).isEqualTo("CLOSED");
        http.send("POST", "/workorder/manage/" + orderId + "/reopen", operatorToken, null);
        assertThat((String) JsonPath.<String>read(
                http.send("GET", "/workorder/manage/" + orderId, operatorToken, null).body(),
                "$.data.status")).isEqualTo("OPEN");

        var notFound = http.send("POST", "/workorder/manage/999999/assign", operatorToken,
                "{\"assigneeId\":202}");
        assertThat(notFound.statusCode()).as(notFound.body()).isEqualTo(400);

        Long creatorId = jdbc.queryForObject("SELECT submitter_id FROM t_work_order WHERE id = " + orderId,
                Long.class);
        assertThat(notificationStore.findByRecipient(creatorId, true))
                .as("分派动作应通过分发器给提交人发站内通知").isNotEmpty();
        List<String> actions = jdbc.queryForList(
                "SELECT action FROM t_work_order_activity WHERE order_id = " + orderId, String.class);
        assertThat(actions).containsExactlyInAnyOrder("assign", "resolve", "close", "reopen");
        assertThat(redis.keys("satoken:*")).as("登录会话应落在 Redis").isNotEmpty();

        var stats = http.send("GET", "/workorder/stats/overview", operatorToken, null);
        assertThat(stats.statusCode()).as(stats.body()).isEqualTo(200);
        assertThat(longOf(JsonPath.read(stats.body(), "$.data.total"))).isEqualTo(1);
        var statsForbidden = http.send("GET", "/workorder/stats/overview", userToken, null);
        assertThat(statsForbidden.statusCode()).as(statsForbidden.body()).isEqualTo(403);

        var export = http.sendBytes("GET", "/workorder/manage/export", operatorToken);
        assertThat(export.statusCode()).isEqualTo(200);
        assertThat((char) export.body()[0]).isEqualTo('P');
        assertThat((char) export.body()[1]).isEqualTo('K');
    }

    @Test
    void suspendResumeUrgeTransferFlowWithComment() throws Exception {
        String operatorToken = grantOperator();
        createUserAndGetId("batch15-flow-user");
        String userToken = loginUser("batch15-flow-user");

        var created = http.send("POST", "/workorder/my", userToken,
                "{\"title\":\"网络中断\",\"content\":\"办公室断网\"}");
        long orderId = longOf(JsonPath.read(created.body(), "$.data.id"));
        http.send("POST", "/workorder/manage/" + orderId + "/assign", operatorToken, "{\"assigneeId\":202}");

        var comment = http.send("POST", "/workorder/my/" + orderId + "/comments", userToken,
                "{\"content\":\"已恢复网络，请继续\",\"visibility\":\"INTERNAL\"}");
        assertThat(comment.statusCode()).as(comment.body()).isEqualTo(200);
        Long commentId = longOf(JsonPath.read(comment.body(), "$.data.id"));
        assertThat(http.send("GET", "/workorder/my/" + orderId + "/comments", userToken, null).body())
                .contains("已恢复网络");

        var userModeration = http.send("DELETE", "/workorder/manage/comments/" + commentId, userToken, null);
        assertThat(userModeration.statusCode()).as(userModeration.body()).isEqualTo(403);
        var moderation = http.send("DELETE", "/workorder/manage/comments/" + commentId, operatorToken, null);
        assertThat(moderation.statusCode()).as(moderation.body()).isEqualTo(200);
        assertThat(http.send("GET", "/workorder/my/" + orderId + "/comments", userToken, null).body())
                .doesNotContain("已恢复网络");

        var userNotificationManage = http.send("GET",
                "/workorder/manage/notifications?recipientId=202", userToken, null);
        assertThat(userNotificationManage.statusCode()).as(userNotificationManage.body()).isEqualTo(403);
        var operatorNotificationManage = http.send("GET",
                "/workorder/manage/notifications?recipientId=202", operatorToken, null);
        assertThat(operatorNotificationManage.statusCode()).as(operatorNotificationManage.body()).isEqualTo(200);

        var suspended = http.send("POST", "/workorder/manage/" + orderId + "/suspend", operatorToken,
                "{\"reason\":\"等待用户补充信息\"}");
        assertThat(suspended.statusCode()).as(suspended.body()).isEqualTo(200);
        assertThat((String) JsonPath.<String>read(
                http.send("GET", "/workorder/manage/" + orderId, operatorToken, null).body(),
                "$.data.status")).isEqualTo("SUSPENDED");
        var resolveBlocked = http.send("POST", "/workorder/manage/" + orderId + "/resolve", operatorToken, null);
        assertThat(resolveBlocked.statusCode()).as(resolveBlocked.body()).isEqualTo(400);
        http.send("POST", "/workorder/manage/" + orderId + "/resume", operatorToken, null);

        var urged = http.send("POST", "/workorder/manage/" + orderId + "/urge", operatorToken,
                "{\"message\":\"请尽快处理\"}");
        assertThat(urged.statusCode()).as(urged.body()).isEqualTo(200);
        assertThat(notificationStore.findByRecipient(202L, true))
                .as("催办应给处理人发通知").isNotEmpty();

        var transferred = http.send("POST", "/workorder/manage/" + orderId + "/transfer", operatorToken,
                "{\"toAssigneeId\":303,\"reason\":\"原处理人转岗\"}");
        assertThat(transferred.statusCode()).as(transferred.body()).isEqualTo(200);
        assertThat(longOf(JsonPath.read(
                http.send("GET", "/workorder/manage/" + orderId, operatorToken, null).body(),
                "$.data.assigneeId"))).isEqualTo(303L);

        var byPriority = http.send("GET", "/workorder/stats/by-priority", operatorToken, null);
        assertThat(byPriority.statusCode()).isEqualTo(200);
        var ranking = http.send("GET", "/workorder/stats/assignee-ranking?limit=5", operatorToken, null);
        assertThat(ranking.statusCode()).isEqualTo(200);
    }
}
