package cn.slainc.workorder.engine;

import cn.slainc.core.exception.BizException;
import cn.slainc.workorder.event.WorkOrderAssignedEvent;
import cn.slainc.workorder.event.WorkOrderCreatedEvent;
import cn.slainc.workorder.event.WorkOrderEscalatedEvent;
import cn.slainc.workorder.event.WorkOrderResumedEvent;
import cn.slainc.workorder.event.WorkOrderSuspendedEvent;
import cn.slainc.workorder.event.WorkOrderTransferredEvent;
import cn.slainc.workorder.event.WorkOrderTransitionedEvent;
import cn.slainc.workorder.event.WorkOrderUrgedEvent;
import cn.slainc.workorder.model.WorkOrder;
import cn.slainc.workorder.number.MemoryWorkOrderSequenceStore;
import cn.slainc.workorder.number.SequenceWorkOrderNumberGenerator;
import cn.slainc.workorder.spi.WorkOrderStore;
import cn.slainc.workorder.store.MemoryWorkOrderStore;
import cn.slainc.workorder.store.MemoryWorkOrderTemplateStore;
import cn.slainc.workorder.template.WorkOrderFieldDefinition;
import cn.slainc.workorder.template.WorkOrderFieldType;
import cn.slainc.workorder.template.WorkOrderFormValidator;
import cn.slainc.workorder.template.WorkOrderTemplateService;
import cn.slainc.workorder.assign.LeastBusyWorkOrderAssignStrategy;
import cn.slainc.workorder.assign.RoundRobinWorkOrderAssignStrategy;
import cn.slainc.workorder.event.WorkOrderResumedEvent;
import cn.slainc.workorder.event.WorkOrderSuspendedEvent;
import cn.slainc.workorder.event.WorkOrderTransferredEvent;
import cn.slainc.workorder.spi.WorkOrderActivityStore;
import cn.slainc.workorder.type.WorkOrderTypeRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class WorkOrderEngineTest {

    private final List<Object> events = new ArrayList<>();

    private MemoryWorkOrderStore store;

    private WorkOrderActivityStore activityStore;

    private WorkOrderEngine engine;

    @BeforeEach
    void setUp() {
        store = new MemoryWorkOrderStore();
        SequenceWorkOrderNumberGenerator generator = new SequenceWorkOrderNumberGenerator(
                new MemoryWorkOrderSequenceStore(), "WO", SequenceWorkOrderNumberGenerator.ResetPeriod.DAILY, 5,
                Clock.fixed(Instant.parse("2026-10-13T03:00:00Z"), ZoneId.of("Asia/Shanghai")));
        WorkOrderTemplateService templateService = new WorkOrderTemplateService(new MemoryWorkOrderTemplateStore());
        templateService.create("generic-template", "通用工单模板", "generic", List.of(
                WorkOrderFieldDefinition.requiredText("faultLocation", "故障位置", 100),
                new WorkOrderFieldDefinition("impactUsers", "影响人数", WorkOrderFieldType.NUMBER, false, null,
                        null, null, null, 0, 100000, null, null, false, 1),
                new WorkOrderFieldDefinition("urgencyLevel", "紧急程度", WorkOrderFieldType.SELECT, false, null,
                        null, null, null, null, null, null, List.of("P0", "P1", "P2"), false, 2)
        ));
        WorkOrderActivityStore activityStore = new cn.slainc.workorder.store.MemoryWorkOrderActivityStore();
        this.activityStore = activityStore;
        engine = new WorkOrderEngine(store, generator, new WorkOrderTypeRegistry(List.of(), List.of()),
                templateService, new WorkOrderFormValidator(), activityStore,
                List.of(new RoundRobinWorkOrderAssignStrategy(), new LeastBusyWorkOrderAssignStrategy()),
                events::add);
    }

    @Test
    void createAssignsUniqueNumberAndPublishesCreatedEvent() {
        WorkOrder first = engine.create("generic", "标题一", "内容", "HIGH", "API", "crm", "BIZ-1", null, 101L,
                Map.of("faultLocation", "上海机房"));
        WorkOrder second = engine.create("generic", "标题二", "内容", null, null, null, null, null, 101L, Map.of("faultLocation", "上海机房"));
        assertThat(first.getNumber()).isEqualTo("WO2026101300001");
        assertThat(second.getNumber()).isEqualTo("WO2026101300002");
        assertThat(first.getStatus()).isEqualTo("OPEN");
        assertThat(first.getSource()).isEqualTo("API");
        assertThat(events).filteredOn(e -> e instanceof WorkOrderCreatedEvent).hasSize(2);
    }

    @Test
    void unknownTypeIsRejected() {
        assertThatThrownBy(() -> engine.create("nope", "标题", "内容", null, null, null, null, null, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未注册的工单类型");
    }

    @Test
    void assignAndCloseWalkStateMachineWithEvents() {
        WorkOrder order = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L, Map.of("faultLocation", "上海机房"));
        engine.assign(order.getId(), 202L, 9L);
        assertThat(engine.get(order.getId()).getStatus()).isEqualTo("IN_PROGRESS");
        engine.transition(order.getId(), "resolve", 202L, java.util.Map.of());
        engine.transition(order.getId(), "close", 9L, java.util.Map.of());
        assertThat(engine.get(order.getId()).getStatus()).isEqualTo("CLOSED");
        assertThat(events).filteredOn(e -> e instanceof WorkOrderTransitionedEvent).hasSize(3);
        assertThat(events).filteredOn(e -> e instanceof WorkOrderAssignedEvent).hasSize(1);
    }

    @Test
    void closedOrderCanBeReopenedViaStateMachine() {
        WorkOrder order = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L, Map.of("faultLocation", "上海机房"));
        engine.transition(order.getId(), "close", 9L, java.util.Map.of());
        engine.transition(order.getId(), "reopen", 9L, java.util.Map.of());
        assertThat(engine.get(order.getId()).getStatus()).isEqualTo("OPEN");
    }

    @Test
    void claimSuspendsResumesAndRecordsActivities() {
        WorkOrder order = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L,
                Map.of("faultLocation", "上海机房"));
        engine.claim(order.getId(), 202L);
        assertThat(engine.get(order.getId()).getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(engine.get(order.getId()).getAssigneeId()).isEqualTo(202L);
        engine.suspend(order.getId(), "等待用户提供信息", 9L);
        assertThat(engine.get(order.getId()).getStatus()).isEqualTo("SUSPENDED");
        engine.resume(order.getId(), 9L);
        assertThat(engine.get(order.getId()).getStatus()).isEqualTo("IN_PROGRESS");
        List<cn.slainc.workorder.model.WorkOrderActivity> activities = engine.activities(order.getId());
        assertThat(activities).extracting(cn.slainc.workorder.model.WorkOrderActivity::action)
                .containsExactly("claim", "suspend", "resume");
        assertThat(events).filteredOn(e -> e instanceof WorkOrderSuspendedEvent).hasSize(1);
        assertThat(events).filteredOn(e -> e instanceof WorkOrderResumedEvent).hasSize(1);
    }

    @Test
    void transferKeepsProgressAndEscalatesWithReason() {
        WorkOrder order = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L,
                Map.of("faultLocation", "上海机房"));
        engine.assign(order.getId(), 202L, 9L);
        engine.transfer(order.getId(), 303L, "原处理人休假", 9L);
        assertThat(engine.get(order.getId()).getAssigneeId()).isEqualTo(303L);
        engine.escalate(order.getId(), 404L, "问题升级", 9L);
        assertThat(engine.get(order.getId()).getAssigneeId()).isEqualTo(404L);
        assertThat(events).filteredOn(e -> e instanceof WorkOrderTransferredEvent).hasSize(1);
        assertThat(events).filteredOn(e -> e instanceof WorkOrderEscalatedEvent).hasSize(1);
        assertThat(engine.activities(order.getId()))
                .extracting(cn.slainc.workorder.model.WorkOrderActivity::reason)
                .contains("原处理人休假", "问题升级");
    }

    @Test
    void urgeRequiresAssigneeAndRecordsActivity() {
        WorkOrder order = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L,
                Map.of("faultLocation", "上海机房"));
        assertThatThrownBy(() -> engine.urge(order.getId(), "快点", 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("尚未指定处理人");
        engine.assign(order.getId(), 202L, 9L);
        engine.urge(order.getId(), "请尽快处理", 9L);
        assertThat(events).filteredOn(e -> e instanceof cn.slainc.workorder.event.WorkOrderUrgedEvent).hasSize(1);
    }

    @Test
    void batchTransitionCollectsSuccessAndFailures() {
        WorkOrder ok = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L,
                Map.of("faultLocation", "上海机房"));
        WorkOrder blocked = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L,
                Map.of("faultLocation", "北京机房"));
        engine.transition(blocked.getId(), "close", 9L, Map.of());
        var result = engine.batchTransition(List.of(ok.getId(), blocked.getId()), "reopen", 9L, Map.of());
        assertThat(result.succeeded()).containsExactly(blocked.getId());
        assertThat(result.failures()).containsOnlyKeys(ok.getId());
    }

    @Test
    void previewReportsAllowanceWithoutMutating() {
        WorkOrder order = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L,
                Map.of("faultLocation", "上海机房"));
        assertThat(engine.preview(order.getId(), "assign").allowed()).isTrue();
        assertThat(engine.preview(order.getId(), "resolve").allowed()).isFalse();
        assertThat(engine.get(order.getId()).getStatus()).isEqualTo("OPEN");
    }

    @Test
    void assignStrategiesPickByRule() {
        WorkOrder order = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L,
                Map.of("faultLocation", "上海机房"));
        List<Long> candidates = List.of(201L, 202L, 203L);
        RoundRobinWorkOrderAssignStrategy roundRobin = new RoundRobinWorkOrderAssignStrategy();
        assertThat(roundRobin.pick(order, candidates, id -> 0)).isEqualTo(201L);
        assertThat(roundRobin.pick(order, candidates, id -> 0)).isEqualTo(202L);
        LeastBusyWorkOrderAssignStrategy leastBusy = new LeastBusyWorkOrderAssignStrategy();
        assertThat(leastBusy.pick(order, candidates, id -> id == 203L ? 5 : 1)).isEqualTo(201L);
        engine.pickAndAssign(order.getId(), candidates, "least-busy", 9L);
        assertThat(engine.get(order.getId()).getAssigneeId()).isEqualTo(201L);
    }

    @Test
    void logicalDeleteHidesOrder() {
        WorkOrder order = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L, Map.of("faultLocation", "上海机房"));
        engine.delete(order.getId());
        assertThatThrownBy(() -> engine.get(order.getId())).isInstanceOf(BizException.class);
        assertThat(store.findByNumber(order.getNumber())).isEmpty();
    }

    @Test
    void customFieldsAreValidatedAgainstTemplate() {
        WorkOrder order = engine.create("generic", "标题", "内容", null, null, null, null, null, 101L,
                java.util.Map.of("faultLocation", "上海机房", "impactUsers", 50));
        assertThat(order.getCustomFields()).containsEntry("impactUsers", 50);
        assertThatThrownBy(() -> engine.create("generic", "标题", "内容", null, null, null, null, null, 101L,
                java.util.Map.of("impactUsers", 50)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("故障位置");
        assertThatThrownBy(() -> engine.create("generic", "标题", "内容", null, null, null, null, null, 101L,
                java.util.Map.of("faultLocation", "上海机房", "impactUsers", 999999)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能大于");
    }
}
