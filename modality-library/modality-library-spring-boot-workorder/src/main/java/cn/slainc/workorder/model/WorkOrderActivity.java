package cn.slainc.workorder.model;


public record WorkOrderActivity(Long orderId, String action, Long operatorId, Long targetUserId, String reason,
                                long createTime) {
}
