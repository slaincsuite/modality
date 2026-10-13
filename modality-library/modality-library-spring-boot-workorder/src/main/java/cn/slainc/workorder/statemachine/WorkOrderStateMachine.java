package cn.slainc.workorder.statemachine;

import cn.slainc.core.exception.BizException;
import cn.slainc.workorder.model.WorkOrder;
import cn.slainc.workorder.model.WorkOrderStateDefinition;
import cn.slainc.workorder.model.WorkOrderTransitionDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;


public class WorkOrderStateMachine {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private final Map<String, WorkOrderStateDefinition> states;

    private final List<WorkOrderTransitionDefinition> transitions;

    private final List<WorkOrderTransitionHook> hooks;

    public WorkOrderStateMachine(List<WorkOrderStateDefinition> states,
                                 List<WorkOrderTransitionDefinition> transitions,
                                 List<WorkOrderTransitionHook> hooks) {
        this.states = states.stream().collect(Collectors.toUnmodifiableMap(WorkOrderStateDefinition::code,
                Function.identity()));
        this.transitions = List.copyOf(transitions);
        this.hooks = hooks == null ? List.of() : List.copyOf(hooks);
    }

    public Set<String> availableActions(String statusCode) {
        return transitions.stream()
                .filter(t -> t.source().equals(statusCode))
                .map(WorkOrderTransitionDefinition::action)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<WorkOrderTransitionDefinition> findTransition(String statusCode, String action) {
        return transitions.stream()
                .filter(t -> t.source().equals(statusCode) && t.action().equals(action))
                .findFirst();
    }

    public WorkOrderStateDefinition state(String statusCode) {
        WorkOrderStateDefinition state = states.get(statusCode);
        if (state == null) {
            throw new BizException("未知的工单状态：" + statusCode);
        }
        return state;
    }

    public boolean canTransition(WorkOrder order, String action, Map<String, Object> variables) {
        Optional<WorkOrderTransitionDefinition> transition = findTransition(order.getStatus(), action);
        return transition.isPresent() && conditionHolds(transition.get(), order, variables);
    }

    public WorkOrderTransitionDefinition transition(WorkOrderTransitionContext context, String action) {
        WorkOrder order = context.order();
        WorkOrderTransitionDefinition transition = findTransition(order.getStatus(), action)
                .orElseThrow(() -> new BizException("工单在状态 " + order.getStatus() + " 下不支持动作：" + action));
        if (!conditionHolds(transition, order, context.variables())) {
            throw new BizException("流转条件不满足，动作 " + action + " 已拦截");
        }
        for (WorkOrderTransitionHook hook : hooks) {
            hook.beforeTransition(context, action, transition.target());
        }
        String source = order.getStatus();
        order.setStatus(transition.target());
        order.setUpdateTime(System.currentTimeMillis());
        order.setVersion(order.getVersion() + 1);
        for (WorkOrderTransitionHook hook : hooks) {
            hook.afterTransition(context, action, source);
        }
        return transition;
    }

    private boolean conditionHolds(WorkOrderTransitionDefinition transition, WorkOrder order,
                                   Map<String, Object> variables) {
        if (transition.conditionSpEL() == null || transition.conditionSpEL().isBlank()) {
            return true;
        }
        StandardEvaluationContext context = new StandardEvaluationContext(order);
        context.setVariable("order", order);
        context.setVariable("vars", variables == null ? Map.of() : variables);
        context.setVariable("now", System.currentTimeMillis());
        Boolean result = PARSER.parseExpression(transition.conditionSpEL()).getValue(context, Boolean.class);
        return result != null && result;
    }
}
