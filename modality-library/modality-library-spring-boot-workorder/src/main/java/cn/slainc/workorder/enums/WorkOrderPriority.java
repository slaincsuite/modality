package cn.slainc.workorder.enums;

import cn.slainc.core.exception.BizException;


public enum WorkOrderPriority {

    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
    URGENT("紧急");

    private final String description;

    WorkOrderPriority(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static WorkOrderPriority parse(String code) {
        for (WorkOrderPriority priority : values()) {
            if (priority.name().equals(code)) {
                return priority;
            }
        }
        throw new BizException("未知的工单优先级：" + code);
    }
}
