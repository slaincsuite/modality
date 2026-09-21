package cn.slainc.core.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumsTest {

    @Test
    void enabledStatus() {
        assertEquals(1, (int) EnabledStatus.ENABLED.value());
        assertEquals("启用", EnabledStatus.ENABLED.label());
        assertTrue(EnabledStatus.ENABLED.enabled());
        assertFalse(EnabledStatus.DISABLED.enabled());
        assertEquals(EnabledStatus.DISABLED, EnabledStatus.of(0));
        assertNull(EnabledStatus.of(9));
        assertNull(EnabledStatus.of(null));
    }

    @Test
    void deleteStatus() {
        assertEquals("已删除", DeleteStatus.DELETED.label());
        assertTrue(DeleteStatus.DELETED.deleted());
        assertFalse(DeleteStatus.NOT_DELETED.deleted());
        assertEquals(DeleteStatus.NOT_DELETED, DeleteStatus.of(null));
        assertEquals(DeleteStatus.DELETED, DeleteStatus.of(1));
        assertEquals(DeleteStatus.NOT_DELETED, DeleteStatus.of(5));
    }

    @Test
    void auditStatus() {
        assertEquals("待审核", AuditStatus.PENDING.label());
        assertEquals(AuditStatus.APPROVED, AuditStatus.of(1));
        assertEquals(AuditStatus.REJECTED, AuditStatus.of(2));
        assertNull(AuditStatus.of(9));
        assertNull(AuditStatus.of(null));
    }

    @Test
    void loginType() {
        assertEquals(LoginType.PLATFORM_ADMIN, LoginType.of("platform_admin"));
        assertEquals(LoginType.APP, LoginType.of("APP"));
        assertNull(LoginType.of("unknown"));
        assertEquals("应用方用户", LoginType.APP.label());
    }

    @Test
    void systemCodeImplementsDictEnum() {
        assertEquals(1L, (long) SystemCode.SUPER_ADMIN.id());
        assertEquals("超级管理员", SystemCode.SUPER_ADMIN.label());
        assertTrue(SystemCode.SUPER_ADMIN instanceof DictEnum);
        assertEquals(SystemCode.SUPER_ADMIN.value(), SystemCode.SUPER_ADMIN.id());
    }
}
