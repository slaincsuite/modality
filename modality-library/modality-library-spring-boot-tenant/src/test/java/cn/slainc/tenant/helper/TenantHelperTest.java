package cn.slainc.tenant.helper;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantHelperTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        while (TenantHelper.isIgnored()) {
            TenantHelper.endIgnore();
        }
    }

    @Test
    @DisplayName("switchTo 返回原租户，restore 可恢复")
    void switchToAndRestore() {
        TenantContext.set(1L);
        Long previous = TenantHelper.switchTo(2L);
        assertEquals(1L, previous);
        assertEquals(2L, TenantHelper.current());
        TenantHelper.restore(previous);
        assertEquals(1L, TenantHelper.current());

        assertEquals(1L, TenantHelper.switchTo(null));
        assertNull(TenantHelper.current());
        assertEquals(1L, TenantHelper.currentOrDefault());
        assertThrows(BizException.class, TenantHelper::require);
        TenantHelper.switchTo(5L);
        assertEquals(5L, TenantHelper.require());
    }

    @Test
    @DisplayName("callWith 与 runWith 在结束后恢复租户")
    void callWithAndRunWith() {
        TenantContext.set(1L);
        assertEquals(9L, TenantHelper.callWith(9L, TenantHelper::require));
        assertEquals(1L, TenantHelper.current());
        TenantHelper.runWith(8L, () -> assertEquals(8L, TenantHelper.current()));
        assertEquals(1L, TenantHelper.current());
    }

    @Test
    @DisplayName("ignore 支持返回值、无返回值与嵌套")
    void ignoreSupportsNesting() {
        assertFalse(TenantHelper.isIgnored());
        assertEquals("done", TenantHelper.ignore(() -> {
            assertTrue(TenantHelper.isIgnored());
            TenantHelper.ignore(() -> assertTrue(TenantHelper.isIgnored()));
            assertTrue(TenantHelper.isIgnored(), "内层结束后外层仍处于忽略状态");
            return "done";
        }));
        assertFalse(TenantHelper.isIgnored());

        TenantHelper.ignore(() -> assertTrue(TenantHelper.isIgnored()));
        assertFalse(TenantHelper.isIgnored(), "无返回值形式也要正确退出忽略状态");

        TenantHelper.beginIgnore();
        assertTrue(TenantHelper.isIgnored());
        TenantHelper.endIgnore();
        assertFalse(TenantHelper.isIgnored());
        TenantHelper.endIgnore();
        assertFalse(TenantHelper.isIgnored(), "多余的 endIgnore 不应产生负深度");
    }
}
