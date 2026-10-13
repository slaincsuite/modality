package cn.slainc.workorder.statemachine;


public interface WorkOrderTransitionHook {

    default void beforeTransition(WorkOrderTransitionContext context, String action, String targetStatus) {
    }

    default void afterTransition(WorkOrderTransitionContext context, String action, String sourceStatus) {
    }
}
