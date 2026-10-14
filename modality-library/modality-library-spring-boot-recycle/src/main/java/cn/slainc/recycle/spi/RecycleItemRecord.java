package cn.slainc.recycle.spi;

import java.time.LocalDateTime;

public record RecycleItemRecord(Long id, String entityType, String entityId, String entityName, String snapshot,
                                String operator, Long operatorId, LocalDateTime deletedAt, LocalDateTime expireAt,
                                Boolean restored, LocalDateTime restoredAt, Long tenantId, String createdBy,
                                LocalDateTime createdAt, String updatedBy, LocalDateTime updatedAt, String remark,
                                Integer version) {
}
