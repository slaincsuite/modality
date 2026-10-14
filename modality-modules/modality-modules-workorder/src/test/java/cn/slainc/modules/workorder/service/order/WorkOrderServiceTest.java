package cn.slainc.modules.workorder.service.order;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.workorder.WorkorderModuleApplication;
import cn.slainc.workorder.engine.BatchTransitionResult;
import cn.slainc.workorder.enums.WorkOrderStatus;
import cn.slainc.modules.workorder.entity.WorkOrderRecord;
import cn.slainc.modules.workorder.entity.WorkOrderReplyRecord;
import cn.slainc.workorder.comment.WorkOrderComment;
import cn.slainc.workorder.subscribe.WorkOrderSubscriptionService;
import cn.slainc.workorder.domain.WorkOrderActivity;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
import cn.slainc.workorder.subscribe.WorkOrderSubscriptionService;
import cn.slainc.workorder.template.WorkOrderFieldDefinition;
import cn.slainc.workorder.template.WorkOrderFieldType;
import cn.slainc.workorder.template.WorkOrderTemplateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 工单状态机与租户、本人隔离：create / reply / assign / resolve / close 全链路
@SpringBootTest(classes = WorkorderModuleApplication.class)
class WorkOrderServiceTest {

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkOrderTemplateService templateService;

    @Autowired
    private cn.slainc.workorder.comment.WorkOrderCommentService commentService;

    @Autowired
    private WorkOrderSubscriptionService subscriptionService;

    @Autowired
    private WorkOrderNotificationStore notificationStore;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        asUser(101L, "alice");
        jdbc.execute("DELETE FROM t_work_order");
        jdbc.execute("DELETE FROM t_work_order_reply");
        if (templateService.findEnabledByType("generic").isEmpty()) {
            templateService.create("generic-template", "通用工单模板", "generic", List.of(
                    new WorkOrderFieldDefinition("impactUsers", "影响人数", WorkOrderFieldType.NUMBER, false, null,
                            null, null, null, 0, 100000, null, null, false, 1)
            ));
        }
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        TenantContext.clear();
    }

    private void asUser(Long userId, String username) {
        UserContext.set(UserContext.UserInfo.of(userId, username, LoginType.TENANT_USER));
    }

    @Test
    void createDefaultsToOpenAndPagesMine() {
        WorkOrderRecord record = workOrderService.create("登录页报错", "点击登录按钮无响应", "HIGH");
        assertThat(record.getStatus()).isEqualTo(WorkOrderStatus.OPEN.name());
        assertThat(record.getPriority()).isEqualTo("HIGH");
        assertThat(record.getSubmitterId()).isEqualTo(101L);
        PageResult<WorkOrderRecord> page = workOrderService.pageMine(1, 10);
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void assignResolveCloseWalksLifecycle() {
        WorkOrderRecord record = workOrderService.create("导出超时", "千行以上导出超时", null);
        assertThat(record.getPriority()).isEqualTo("MEDIUM");
        workOrderService.assign(record.getId(), 202L);
        WorkOrderRecord assigned = workOrderService.requireOrder(record.getId());
        assertThat(assigned.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS.name());
        assertThat(assigned.getAssigneeId()).isEqualTo(202L);
        workOrderService.resolve(record.getId());
        workOrderService.close(record.getId());
        assertThat(workOrderService.requireOrder(record.getId()).getStatus())
                .isEqualTo(WorkOrderStatus.CLOSED.name());
        assertThatThrownBy(() -> workOrderService.reply(record.getId(), "继续补充"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> workOrderService.resolve(record.getId()))
                .isInstanceOf(BizException.class);
    }

    @Test
    void replyRecordsReplierAndContent() {
        WorkOrderRecord record = workOrderService.create("数据看板空白", "看板页面空白", null);
        asUser(202L, "bob");
        workOrderService.reply(record.getId(), "已定位，正在修复");
        List<WorkOrderReplyRecord> replies = workOrderService.replies(record.getId());
        assertThat(replies).hasSize(1);
        assertThat(replies.get(0).getReplierId()).isEqualTo(202L);
        assertThat(replies.get(0).getContent()).isEqualTo("已定位，正在修复");
    }

    @Test
    void nonOwnerCannotOperatePortalSide() {
        WorkOrderRecord record = workOrderService.create("他人工单", "内容", null);
        asUser(202L, "bob");
        assertThatThrownBy(() -> workOrderService.requireOwnOrder(record.getId()))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> workOrderService.closeOwn(record.getId()))
                .isInstanceOf(BizException.class);
        workOrderService.close(record.getId());
    }

    @Test
    void managePageFiltersByPriorityAndKeyword() {
        workOrderService.create("高优工单", "内容一", "URGENT");
        workOrderService.create("普通工单", "内容二", null);
        PageResult<WorkOrderRecord> urgent = workOrderService.pageManage(null, "URGENT", null, 1, 10);
        assertThat(urgent.total()).isEqualTo(1);
        PageResult<WorkOrderRecord> all = workOrderService.pageManage(null, null, null, 1, 10);
        assertThat(all.total()).isEqualTo(2);
        PageResult<WorkOrderRecord> byKeyword = workOrderService.pageManage(null, null, "高优", 1, 10);
        assertThat(byKeyword.total()).isEqualTo(1);
    }

    @Test
    void tenantIsolationHidesOtherTenantOrders() {
        workOrderService.create("租户一工单", "内容", null);
        TenantContext.set(2L);
        asUser(201L, "carol");
        workOrderService.create("租户二工单", "内容", null);
        PageResult<WorkOrderRecord> mine = workOrderService.pageMine(1, 10);
        assertThat(mine.total()).isEqualTo(1);
        assertThat(mine.records().get(0).getTitle()).isEqualTo("租户二工单");
    }

    @Test
    void customFieldsRoundTripThroughJsonColumn() {
        WorkOrderRecord record = workOrderService.create("带自定义字段", "内容", "HIGH",
                Map.of("impactUsers", 88));
        String stored = jdbc.queryForObject(
                "SELECT custom_fields FROM t_work_order WHERE id = " + record.getId(), String.class);
        assertThat(stored).contains("impactUsers").contains("88");
    }

    @Test
    void commentSubscriptionNotificationPersistThroughMpStores() {
        WorkOrderRecord record = workOrderService.create("落库验证", "内容", "HIGH");
        subscriptionService.subscribeOrder(record.getId(), 101L);
        commentService.add(record.getId(), 101L, "留言", WorkOrderComment.VISIBILITY_INTERNAL, null, null);
        asUser(9L, "op");
        workOrderService.assign(record.getId(), 202L);
        Integer comments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_work_order_comment WHERE order_id = " + record.getId(), Integer.class);
        Integer subscriptions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_work_order_subscription WHERE order_id = " + record.getId(), Integer.class);
        Integer notifications = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_work_order_notification WHERE recipient_id = 101", Integer.class);
        assertThat(comments).isEqualTo(1);
        assertThat(subscriptions).isEqualTo(1);
        assertThat(notifications).isGreaterThanOrEqualTo(1);
    }

    @Test
    void customFieldsOutOfRangeValueIsRejected() {
        assertThatThrownBy(() -> workOrderService.create("超范围", "内容", null, Map.of("impactUsers", 999999)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能大于");
    }

    @Test
    void claimTransferSuspendResumeViaEngine() {
        WorkOrderRecord record = workOrderService.create("认领转派全链路", "点击登录按钮无响应", null);
        workOrderService.claim(record.getId());
        WorkOrderRecord claimed = workOrderService.requireOrder(record.getId());
        assertThat(claimed.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS.name());
        assertThat(claimed.getAssigneeId()).isEqualTo(101L);
        WorkOrderRecord suspended = workOrderService.suspend(record.getId(), "等待用户");
        assertThat(suspended.getStatus()).isEqualTo(WorkOrderStatus.SUSPENDED.name());
        workOrderService.resume(record.getId());
        asUser(9L, "op");
        WorkOrderRecord transferred = workOrderService.transfer(record.getId(), 303L, "回溯");
        assertThat(transferred.getAssigneeId()).isEqualTo(303L);
        List<WorkOrderActivity> activities = workOrderService.activities(record.getId());
        assertThat(activities).extracting(WorkOrderActivity::action).contains("claim", "transfer");
    }

    @Test
    void batchReopenClosesThenReopens() {
        WorkOrderRecord one = workOrderService.create("批量重开一", "内容一", null);
        WorkOrderRecord two = workOrderService.create("批量重开二", "内容二", null);
        asUser(9L, "op");
        workOrderService.close(one.getId());
        workOrderService.close(two.getId());
        BatchTransitionResult result = workOrderService.batchReopen(List.of(one.getId(), two.getId()));
        assertThat(result.succeeded()).containsExactlyInAnyOrder(one.getId(), two.getId());
        assertThat(workOrderService.requireOrder(one.getId()).getStatus())
                .isEqualTo(WorkOrderStatus.OPEN.name());
        assertThat(workOrderService.requireOrder(two.getId()).getStatus())
                .isEqualTo(WorkOrderStatus.OPEN.name());
    }

    @Test
    void subscribeThenClaimNotifiesSubscriber() {
        WorkOrderRecord record = workOrderService.create("订阅通知链路", "认领后订阅者收到站内通知", null);
        subscriptionService.subscribeOrder(record.getId(), 999L);
        subscriptionService.subscribeOrder(record.getId(), 999L);
        assertThat(subscriptionService.orderSubscribers(record.getId())).containsExactly(999L);
        workOrderService.claim(record.getId());
        assertThat(notificationStore.countUnread(999L)).isGreaterThanOrEqualTo(1L);
        assertThat(subscriptionService.unsubscribeOrder(record.getId(), 999L)).isTrue();
        assertThat(subscriptionService.orderSubscribers(record.getId())).isEmpty();
        assertThat(subscriptionService.unsubscribeOrder(record.getId(), 999L)).isFalse();
    }
}
