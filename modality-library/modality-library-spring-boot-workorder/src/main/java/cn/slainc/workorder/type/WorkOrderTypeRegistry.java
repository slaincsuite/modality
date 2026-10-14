package cn.slainc.workorder.type;

import cn.slainc.core.exception.BizException;
import cn.slainc.workorder.domain.WorkOrderStateMachines;
import cn.slainc.workorder.domain.WorkOrderStateDefinition;
import cn.slainc.workorder.domain.WorkOrderTransitionDefinition;
import cn.slainc.workorder.domain.WorkOrderTypeDefinition;
import cn.slainc.workorder.spi.WorkOrderTypeProvider;
import cn.slainc.workorder.statemachine.WorkOrderStateMachine;
import cn.slainc.workorder.statemachine.WorkOrderTransitionHook;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class WorkOrderTypeRegistry {

    private final Map<String, WorkOrderTypeDefinition> types = new LinkedHashMap<>();

    private final Map<String, WorkOrderStateMachine> machines = new LinkedHashMap<>();

    private final WorkOrderStateMachine defaultMachine;

    public WorkOrderTypeRegistry(List<WorkOrderTypeProvider> providers,
                                 List<WorkOrderTransitionHook> hooks) {
        List<WorkOrderTypeDefinition> all = new ArrayList<>(List.of(
                WorkOrderTypeDefinition.generic(), WorkOrderTypeDefinition.approval(),
                WorkOrderTypeDefinition.fault()));
        for (WorkOrderTypeProvider provider : providers == null ? List.<WorkOrderTypeProvider>of() : providers) {
            all.addAll(provider.register());
        }
        for (WorkOrderTypeDefinition type : all) {
            if (types.put(type.code(), type) != null) {
                throw new BizException("工单类型编码重复：" + type.code());
            }
            machines.put(type.code(), new WorkOrderStateMachine(type.states(), type.transitions(), hooks));
        }
        List<WorkOrderStateDefinition> states = WorkOrderStateMachines.defaultStates();
        List<WorkOrderTransitionDefinition> transitions = WorkOrderStateMachines.defaultTransitions();
        this.defaultMachine = new WorkOrderStateMachine(states, transitions, hooks);
    }

    public WorkOrderTypeDefinition type(String typeCode) {
        WorkOrderTypeDefinition type = types.get(typeCode);
        if (type == null) {
            throw new BizException("未注册的工单类型：" + typeCode);
        }
        return type;
    }

    public WorkOrderStateMachine machineFor(String typeCode) {
        return machines.getOrDefault(typeCode, defaultMachine);
    }

    public List<WorkOrderTypeDefinition> all() {
        return List.copyOf(types.values());
    }
}
