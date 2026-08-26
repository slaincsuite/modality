package cn.slainc.tenant.context;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantContextLifecycleTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("上下文默认为空，getOrDefault 返回默认租户")
    void defaultContextIsEmpty() {
        assertNull(TenantContext.get(), "未设置租户时上下文应为空");
        assertEquals(1L, TenantContext.DEFAULT_TENANT_ID);
        assertEquals(1L, TenantContext.getOrDefault());
    }

    @Test
    @DisplayName("set/get/clear 生命周期正确")
    void setGetAndClear() {
        TenantContext.set(9L);
        assertEquals(9L, TenantContext.get());
        TenantContext.set(null);
        assertNull(TenantContext.get(), "set(null) 应清空上下文");
        TenantContext.set(3L);
        TenantContext.clear();
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("缺少租户时 require 抛出中文业务异常")
    void requireFailsWithoutTenant() {
        BizException exception = assertThrows(BizException.class, TenantContext::require);
        assertTrue(exception.getMessage().contains("缺少租户"), "异常消息应为中文提示：" + exception.getMessage());
    }

    @Test
    @DisplayName("callWith 结束后恢复原租户")
    void callWithRestoresPrevious() {
        TenantContext.set(1L);
        String result = TenantContext.callWith(2L, () -> {
            assertEquals(2L, TenantContext.get());
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(1L, TenantContext.get());
    }

    @Test
    @DisplayName("嵌套 callWith 逐层恢复，最外层清空后回到空")
    void nestedCallWithRestoresLayerByLayer() {
        assertNull(TenantContext.get());
        TenantContext.runWith(1L, () -> {
            assertEquals(1L, TenantContext.get());
            TenantContext.runWith(2L, () -> {
                assertEquals(2L, TenantContext.get());
                TenantContext.runWith(3L, () -> assertEquals(3L, TenantContext.get()));
                assertEquals(2L, TenantContext.get());
            });
            assertEquals(1L, TenantContext.get());
        });
        assertNull(TenantContext.get(), "嵌套调用结束后应回到空上下文");
    }

    @Test
    @DisplayName("callWith 抛出异常时同样恢复原租户")
    void callWithRestoresOnException() {
        TenantContext.set(1L);
        assertThrows(IllegalStateException.class, () -> TenantContext.callWith(2L, () -> {
            throw new IllegalStateException("业务失败");
        }));
        assertEquals(1L, TenantContext.get());
        TenantContext.clear();
        assertThrows(IllegalStateException.class, () -> TenantContext.callWith(2L, () -> {
            throw new IllegalStateException("业务失败");
        }));
        assertNull(TenantContext.get());
    }

    @Test
    @DisplayName("runWith 设置并恢复租户上下文")
    void runWithSetsAndRestores() {
        StringBuilder trace = new StringBuilder();
        TenantContext.runWith(6L, () -> trace.append(TenantContext.get()));
        assertEquals("6", trace.toString());
        assertNull(TenantContext.get());
    }
}
