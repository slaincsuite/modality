package cn.slainc.core.enums;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineTest {

    enum OrderState {
        CREATED, PAID, SHIPPED, CLOSED
    }

    private StateMachine<OrderState> machine() {
        return StateMachine.<OrderState>builder(OrderState.class)
                .initial(OrderState.CREATED)
                .allow(OrderState.CREATED, OrderState.PAID)
                .allow(OrderState.CREATED, OrderState.CLOSED)
                .allow(OrderState.PAID, OrderState.SHIPPED)
                .build();
    }

    @Test
    void allowsConfiguredTransitions() {
        StateMachine<OrderState> machine = machine();
        assertEquals(OrderState.CREATED, machine.initial());
        assertTrue(machine.canTransit(OrderState.CREATED, OrderState.PAID));
        assertTrue(machine.canTransit(OrderState.CREATED, OrderState.CLOSED));
        assertTrue(machine.canTransit(OrderState.PAID, OrderState.SHIPPED));
        assertFalse(machine.canTransit(OrderState.CREATED, OrderState.SHIPPED));
        assertFalse(machine.canTransit(OrderState.SHIPPED, OrderState.CREATED));
    }

    @Test
    void rejectsNullStates() {
        StateMachine<OrderState> machine = machine();
        assertFalse(machine.canTransit(null, OrderState.PAID));
        assertFalse(machine.canTransit(OrderState.PAID, null));
    }

    @Test
    void exposesAllowedTargets() {
        StateMachine<OrderState> machine = machine();
        assertEquals(Set.of(OrderState.PAID, OrderState.CLOSED), machine.allowedTargets(OrderState.CREATED));
        assertTrue(machine.allowedTargets(OrderState.SHIPPED).isEmpty());
        assertEquals(2, machine.transitions().size());
        assertThrows(UnsupportedOperationException.class,
                () -> machine.transitions().put(OrderState.PAID, Set.of()));
    }

    @Test
    void checkTransitThrowsBizException() {
        StateMachine<OrderState> machine = machine();
        machine.checkTransit(OrderState.CREATED, OrderState.PAID);
        BizException exception = assertThrows(BizException.class,
                () -> machine.checkTransit(OrderState.SHIPPED, OrderState.CREATED));
        assertEquals(ResultCode.DATA_STATE_INVALID.code(), exception.code());
        assertTrue(exception.getMessage().contains("SHIPPED"));
    }

    @Test
    void defaultsToFirstConstant() {
        StateMachine<OrderState> machine = StateMachine.<OrderState>builder(OrderState.class).build();
        assertEquals(OrderState.CREATED, machine.initial());
        assertTrue(machine.transitions().isEmpty());
    }
}
