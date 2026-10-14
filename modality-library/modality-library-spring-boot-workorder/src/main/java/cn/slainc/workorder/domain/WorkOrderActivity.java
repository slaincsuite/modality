package cn.slainc.workorder.domain;


public record WorkOrderActivity(Long orderId, String action, Long operatorId, Long targetUserId, String reason,
                                long createTime) {
}
