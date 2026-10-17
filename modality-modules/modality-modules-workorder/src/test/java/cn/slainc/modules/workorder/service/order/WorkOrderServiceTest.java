package cn.slainc.modules.workorder.service.order;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.workorder.WorkorderModuleApplication;
import cn.slainc.modules.workorder.dto.WorkOrderCommentRequest;
import cn.slainc.modules.workorder.dto.WorkOrderFilter;
import cn.slainc.modules.workorder.web.manage.WorkOrderManageController;
import cn.slainc.workorder.engine.BatchTransitionResult;
import cn.slainc.workorder.enums.WorkOrderStatus;
import cn.slainc.modules.workorder.entity.WorkOrderRecord;
import cn.slainc.modules.workorder.entity.WorkOrderReplyRecord;
import cn.slainc.workorder.comment.WorkOrderComment;
import cn.slainc.workorder.subscribe.WorkOrderSubscriptionService;
import cn.slainc.workorder.subscribe.WorkOrderSubscription;
import cn.slainc.workorder.domain.WorkOrderActivity;
import cn.slainc.workorder.spi.WorkOrderNotificationStore;
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
import java.time.LocalDate;
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

    @Autowired
    private WorkOrderManageController manageController;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        asUser(101L, "alice");
        jdbc.execute("DELETE FROM t_work_order");
        jdbc.execute("DELETE FROM t_work_order_reply");
        jdbc.execute("DELETE FROM t_work_order_activity");
        jdbc.execute("DELETE FROM t_work_order_comment");
        jdbc.execute("DELETE FROM t_work_order_subscription");
        jdbc.execute("DELETE FROM t_work_order_notification");
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
        PageResult<WorkOrderRecord> page = workOrderService.pageMine(WorkOrderFilter.empty(), 1, 10);
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
        PageResult<WorkOrderRecord> urgent = workOrderService.pageManage(
                new WorkOrderFilter(null, "URGENT", null, null, null, null), 1, 10);
        assertThat(urgent.total()).isEqualTo(1);
        PageResult<WorkOrderRecord> all = workOrderService.pageManage(WorkOrderFilter.empty(), 1, 10);
        assertThat(all.total()).isEqualTo(2);
        PageResult<WorkOrderRecord> byKeyword = workOrderService.pageManage(
                new WorkOrderFilter(null, null, null, "高优", null, null), 1, 10);
        assertThat(byKeyword.total()).isEqualTo(1);
    }

    @Test
    void tenantIsolationHidesOtherTenantOrders() {
        workOrderService.create("租户一工单", "内容", null);
        TenantContext.set(2L);
        asUser(201L, "carol");
        workOrderService.create("租户二工单", "内容", null);
        PageResult<WorkOrderRecord> mine = workOrderService.pageMine(WorkOrderFilter.empty(), 1, 10);
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
        BatchTransitionResult result = workOrderService.batchReopen(List.of(one.getId(), two.getId()),
                null, null, null, null);
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

    @Test
    void createWithTypeCodeUsesRegisteredType() {
        WorkOrderRecord approval = workOrderService.create("approval", "报销审批", "差旅报销", "LOW", null);
        assertThat(approval.getTypeCode()).isEqualTo("approval");
        WorkOrderRecord defaultType = workOrderService.create("默认类型", "不传类型", null);
        assertThat(defaultType.getTypeCode()).isEqualTo(WorkOrderService.DEFAULT_TYPE_CODE);
        assertThatThrownBy(() -> workOrderService.create("unknown", "未知类型", "内容", null, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未注册的工单类型");
    }

    @Test
    void myPageFiltersByStatusPriorityTypeAndKeyword() {
        WorkOrderRecord high = workOrderService.create("登录失败", "点击登录无响应", "HIGH");
        workOrderService.create("approval", "报销申请", "差旅费用报销", "LOW", null);
        asUser(202L, "bob");
        workOrderService.create("他人的紧急单", "内容", "URGENT");
        asUser(101L, "alice");

        PageResult<WorkOrderRecord> combined = workOrderService.pageMine(
                new WorkOrderFilter("OPEN", "HIGH", "generic", "登录", null, null), 1, 10);
        assertThat(combined.total()).isEqualTo(1);
        assertThat(combined.records().get(0).getId()).isEqualTo(high.getId());
        assertThat(workOrderService.pageMine(
                new WorkOrderFilter(null, null, "approval", null, null, null), 1, 10).total())
                .isEqualTo(1);
        assertThat(workOrderService.pageMine(
                new WorkOrderFilter(null, null, null, "报销", null, null), 1, 10).total())
                .isEqualTo(1);
        assertThat(workOrderService.pageMine(
                new WorkOrderFilter(null, null, null, high.getNumber(), null, null), 1, 10).total())
                .isEqualTo(1);
    }

    @Test
    void managePageFiltersByTypeCodeAndCreateTime() {
        WorkOrderRecord generic = workOrderService.create("十天前的通用单", "内容", null);
        WorkOrderRecord approval = workOrderService.create("approval", "今天的审批单", "内容", null, null);
        long now = System.currentTimeMillis();
        jdbc.update("UPDATE t_work_order SET create_time = ? WHERE id = ?",
                now - 10L * 24 * 60 * 60 * 1000, generic.getId());
        jdbc.update("UPDATE t_work_order SET create_time = ? WHERE id = ?", now, approval.getId());

        PageResult<WorkOrderRecord> byType = workOrderService.pageManage(
                new WorkOrderFilter(null, null, "approval", null, null, null), 1, 10);
        assertThat(byType.total()).isEqualTo(1);
        assertThat(byType.records().get(0).getId()).isEqualTo(approval.getId());

        PageResult<WorkOrderRecord> byEpochRange = workOrderService.pageManage(
                new WorkOrderFilter(null, null, null, null,
                        String.valueOf(now - 5L * 24 * 60 * 60 * 1000), String.valueOf(now + 1000)),
                1, 10);
        assertThat(byEpochRange.total()).isEqualTo(1);
        assertThat(byEpochRange.records().get(0).getId()).isEqualTo(approval.getId());

        PageResult<WorkOrderRecord> byDateRange = workOrderService.pageManage(
                new WorkOrderFilter(null, null, null, null, LocalDate.now().toString(),
                        LocalDate.now().toString()), 1, 10);
        assertThat(byDateRange.total()).isEqualTo(1);
    }

    @Test
    void managePageRejectsInvalidTimeRange() {
        assertThatThrownBy(() -> workOrderService.pageManage(
                new WorkOrderFilter(null, null, null, null, "不是日期", null), 1, 10))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("提交时间格式不正确");
        assertThatThrownBy(() -> workOrderService.pageManage(
                new WorkOrderFilter(null, null, null, null, "2026-10-18", "2026-10-17"), 1, 10))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("开始时间不能晚于结束时间");
    }

    @Test
    void batchReopenByFilterOnlyReopensMatchedClosedOrders() {
        WorkOrderRecord genericClosed = workOrderService.create("通用已关闭", "内容", null);
        WorkOrderRecord approvalClosed = workOrderService.create("approval", "审批已关闭", "内容", null, null);
        WorkOrderRecord genericOpen = workOrderService.create("通用未关闭", "内容", null);
        asUser(9L, "op");
        workOrderService.close(genericClosed.getId());
        workOrderService.close(approvalClosed.getId());

        BatchTransitionResult approvalResult = workOrderService.batchReopen(
                null, "CLOSED", "approval", null, null);
        assertThat(approvalResult.succeeded()).containsExactly(approvalClosed.getId());
        assertThat(workOrderService.requireOrder(approvalClosed.getId()).getStatus())
                .isEqualTo(WorkOrderStatus.OPEN.name());
        assertThat(workOrderService.requireOrder(genericClosed.getId()).getStatus())
                .isEqualTo(WorkOrderStatus.CLOSED.name());
        assertThat(workOrderService.requireOrder(genericOpen.getId()).getStatus())
                .isEqualTo(WorkOrderStatus.OPEN.name());

        BatchTransitionResult genericResult = workOrderService.batchReopen(
                null, "CLOSED", "generic", null, null);
        assertThat(genericResult.succeeded()).containsExactly(genericClosed.getId());
    }

    @Test
    void subscriptionStateReflectsSubscriber() {
        WorkOrderRecord record = workOrderService.create("订阅状态", "内容", null);
        assertThat(subscriptionService.orderSubscription(record.getId(), 101L)).isEmpty();
        subscriptionService.subscribeOrder(record.getId(), 101L);
        var subscription = subscriptionService.orderSubscription(record.getId(), 101L);
        assertThat(subscription).isPresent();
        assertThat(subscription.get().getSubscriberId()).isEqualTo(101L);
        assertThat(subscriptionService.orderSubscription(record.getId(), 202L)).isEmpty();
        assertThatThrownBy(() -> subscriptionService.orderSubscription(999999L, 101L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("工单不存在");
    }

    @Test
    @SuppressWarnings("unchecked")
    void manageCanReadAllCommentsAndReplyToOthersOrder() {
        WorkOrderRecord record = workOrderService.create("评论管理", "内容", null);
        commentService.add(record.getId(), 101L, "内部评论", WorkOrderComment.VISIBILITY_INTERNAL, null, null);
        commentService.add(record.getId(), 101L, "外部评论", WorkOrderComment.VISIBILITY_EXTERNAL, null, null);
        asUser(9L, "op");

        List<WorkOrderComment> comments = (List<WorkOrderComment>) manageController.comments(record.getId());
        assertThat(comments).hasSize(2);
        WorkOrderComment reply = (WorkOrderComment) manageController.addComment(record.getId(),
                new WorkOrderCommentRequest("管理员回复", null, null, null));
        assertThat(reply.getAuthorId()).isEqualTo(9L);
        assertThat(reply.getVisibility()).isEqualTo(WorkOrderComment.VISIBILITY_INTERNAL);
        WorkOrderComment defaultVisibility = commentService.add(record.getId(), 9L, "默认可见性",
                null, null, null);
        assertThat(defaultVisibility.getVisibility()).isEqualTo(WorkOrderComment.VISIBILITY_INTERNAL);
    }
}
