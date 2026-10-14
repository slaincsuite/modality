package cn.slainc.modules.workorder.service.stats;

import cn.slainc.modules.workorder.service.order.WorkOrderService;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.workorder.WorkorderModuleApplication;
import cn.slainc.modules.workorder.entity.WorkOrderRecord;
import cn.slainc.workorder.enums.WorkOrderStatus;
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

@SpringBootTest(classes = WorkorderModuleApplication.class)
class WorkOrderStatsServiceTest {

    @Autowired
    private WorkOrderStatsService statsService;

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private WorkOrderTemplateService templateService;

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

    private WorkOrderRecord createdResolved() {
        WorkOrderRecord record = workOrderService.create("统计解决工单", "已指派并解决", "HIGH");
        workOrderService.assign(record.getId(), 202L);
        workOrderService.resolve(record.getId());
        return record;
    }

    @Test
    void overviewCountsTotalTodayNewAndStatusBuckets() {
        workOrderService.create("统计紧急工单", "紧急优先级", "URGENT");
        createdResolved();
        workOrderService.create("统计普通工单", "默认优先级", null);
        Map<String, Object> overview = statsService.overview();
        assertThat(overview.get("total")).isEqualTo(3L);
        assertThat(overview.get("todayNew")).isEqualTo(3L);
        assertThat(overview.get("open")).isEqualTo(2L);
        assertThat(overview.get("inProgress")).isEqualTo(0L);
        assertThat(overview.get("resolved")).isEqualTo(1L);
        assertThat(overview.get("closed")).isEqualTo(0L);
    }

    @Test
    void distributionsGroupByStatusPriorityAndType() {
        workOrderService.create("分布紧急工单", "紧急优先级", "URGENT");
        createdResolved();
        workOrderService.create("分布普通工单", "默认优先级", null);
        assertThat(findValue(statsService.byStatus(), WorkOrderStatus.OPEN.name())).isEqualTo(2L);
        assertThat(findValue(statsService.byStatus(), WorkOrderStatus.RESOLVED.name())).isEqualTo(1L);
        assertThat(findValue(statsService.byPriority(), "URGENT")).isEqualTo(1L);
        assertThat(findValue(statsService.byPriority(), "HIGH")).isEqualTo(1L);
        assertThat(findValue(statsService.byPriority(), "MEDIUM")).isEqualTo(1L);
        assertThat(findValue(statsService.byType(), "generic")).isEqualTo(3L);
    }

    @Test
    void assigneeRankingOrdersByHandledCount() {
        WorkOrderRecord first = workOrderService.create("排行工单一", "内容", null);
        WorkOrderRecord second = workOrderService.create("排行工单二", "内容", null);
        workOrderService.assign(first.getId(), 202L);
        workOrderService.assign(second.getId(), 202L);
        WorkOrderRecord third = workOrderService.create("排行工单三", "内容", null);
        workOrderService.assign(third.getId(), 303L);
        List<Map<String, Object>> ranking = statsService.assigneeRanking(10);
        assertThat(ranking.get(0).get("name")).isEqualTo("202");
        assertThat(ranking.get(0).get("value")).isEqualTo(2L);
        assertThat(ranking.get(1).get("name")).isEqualTo("303");
        assertThat(ranking.get(1).get("value")).isEqualTo(1L);
        assertThat(statsService.assigneeRanking(1)).hasSize(1);
    }

    @Test
    void avgResolutionMillisReportsResolvedDuration() {
        assertThat(statsService.avgResolutionMillis()).isZero();
        createdResolved();
        assertThat(statsService.avgResolutionMillis()).isGreaterThanOrEqualTo(0L);
    }

    private long findValue(List<Map<String, Object>> distributions, String name) {
        return distributions.stream()
                .filter(item -> name.equals(item.get("name")))
                .findFirst()
                .map(item -> (Long) item.get("value"))
                .orElseThrow();
    }
}
