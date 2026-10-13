package cn.slainc.workorder.enums;

import cn.slainc.core.exception.BizException;


public enum WorkOrderStatus {

    OPEN("待处理"),
    IN_PROGRESS("处理中"),
    SUSPENDED("已挂起"),
    RESOLVED("已解决"),
    CLOSED("已关闭");

    private final String description;

    WorkOrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static WorkOrderStatus parse(String code) {
        for (WorkOrderStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new BizException("未知的工单状态：" + code);
    }
}
