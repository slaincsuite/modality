package cn.slainc.changeaudit.spi;

import java.io.Serializable;
import java.time.LocalDateTime;

public record ChangeRecord(String entityType, String entityId, String entityName, String operation,
                           String changes, Integer changedFields, String operator, Long operatorId,
                           String requestUri, String traceId, String changeSource, LocalDateTime changeTime,
                           Long tenantId) implements Serializable {

    private static final long serialVersionUID = 1L;
}
