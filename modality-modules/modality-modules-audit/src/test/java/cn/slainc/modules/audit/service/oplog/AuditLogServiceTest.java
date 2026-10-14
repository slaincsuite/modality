package cn.slainc.modules.audit.service.oplog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogServiceTest {

    @Test
    void exportLimitIsClampedToUpperBound() {
        assertThat(AuditLogService.clampLimit(10_000_000)).isEqualTo(AuditLogService.MAX_EXPORT_LIMIT);
        assertThat(AuditLogService.clampLimit(AuditLogService.MAX_EXPORT_LIMIT + 1))
                .isEqualTo(AuditLogService.MAX_EXPORT_LIMIT);
        assertThat(AuditLogService.clampLimit(500)).isEqualTo(500);
        assertThat(AuditLogService.clampLimit(0)).isEqualTo(1);
        assertThat(AuditLogService.clampLimit(-5)).isEqualTo(1);
    }
}
