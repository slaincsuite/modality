package cn.slainc.workorder.statemachine;

import cn.slainc.core.exception.BizException;
import cn.slainc.workorder.domain.WorkOrder;
import cn.slainc.workorder.domain.WorkOrderStateMachines;
import cn.slainc.workorder.domain.WorkOrderTransitionDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;


class WorkOrderStateMachineTest {

    private final List<String> timeline = new ArrayList<>();

    private WorkOrderStateMachine machineWithFastLane() {
        List<WorkOrderTransitionDefinition> transitions = List.of(
                WorkOrderTransitionDefinition.of("OPEN", "IN_PROGRESS", "assign", "受理"),
                new WorkOrderTransitionDefinition("OPEN", "RESOLVED", "fastLane", "快速通道",
                        "#order.priority == 'URGENT'", null, 1),
                WorkOrderTransitionDefinition.of("IN_PROGRESS", "RESOLVED", "resolve", "标记解决"));
        return new WorkOrderStateMachine(WorkOrderStateMachines.defaultStates(), transitions,
                List.of(new WorkOrderTransitionHook() {

                    @Override
                    public void beforeTransition(WorkOrderTransitionContext context, String action,
                                                 String targetStatus) {
                        timeline.add("before:" + action);
                    }

                    @Override
                    public void afterTransition(WorkOrderTransitionContext context, String action,
                                                String sourceStatus) {
                        timeline.add("after:" + action);
                    }
                }));
    }

    private WorkOrder order(String priority) {
        WorkOrder order = new WorkOrder();
        order.setStatus("OPEN");
        order.setPriority(priority);
        return order;
    }

    @Test
    void legalTransitionMutatesStatusAndFiresHooksInOrder() {
        WorkOrderStateMachine machine = machineWithFastLane();
        WorkOrder order = order("MEDIUM");
        WorkOrderTransitionDefinition definition = machine.transition(
                new WorkOrderTransitionContext(order, 9L, Map.of()), "assign");
        assertThat(definition.target()).isEqualTo("IN_PROGRESS");
        assertThat(order.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(order.getVersion()).isEqualTo(1);
        assertThat(timeline).containsExactly("before:assign", "after:assign");
    }

    @Test
    void unsupportedActionIsRejected() {
        WorkOrderStateMachine machine = machineWithFastLane();
        WorkOrder order = order("MEDIUM");
        assertThatThrownBy(() -> machine.transition(WorkOrderTransitionContext.of(order, 9L), "resolve"))
                .isInstanceOf(BizException.class);
        assertThat(machine.availableActions("OPEN")).isEqualTo(Set.of("assign", "fastLane"));
    }

    @Test
    void spelConditionGuardsFastLane() {
        WorkOrderStateMachine machine = machineWithFastLane();
        WorkOrder urgent = order("URGENT");
        assertThat(machine.canTransition(urgent, "fastLane", Map.of())).isTrue();
        machine.transition(new WorkOrderTransitionContext(urgent, 9L, Map.of()), "fastLane");
        assertThat(urgent.getStatus()).isEqualTo("RESOLVED");
        WorkOrder normal = order("MEDIUM");
        assertThat(machine.canTransition(normal, "fastLane", Map.of())).isFalse();
        assertThatThrownBy(() -> machine.transition(WorkOrderTransitionContext.of(normal, 9L), "fastLane"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("流转条件不满足");
    }
}
