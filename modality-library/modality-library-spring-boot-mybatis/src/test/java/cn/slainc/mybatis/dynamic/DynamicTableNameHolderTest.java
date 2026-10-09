package cn.slainc.mybatis.dynamic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DynamicTableNameHolderTest {

    private final DefaultTableNameHandler handler = new DefaultTableNameHandler();

    @AfterEach
    void clearHolder() {
        DynamicTableNameHolder.clear();
    }

    @Test
    @DisplayName("run 作用域内动态表名生效，退出后自动清理")
    void runAppliesAndClearsMapping() {
        DynamicTableNameHolder.run("t_order", "t_order_202401", () -> {
            assertEquals("t_order_202401", DynamicTableNameHolder.get("t_order"));
            assertEquals("t_order_202401", handler.dynamicTableName("SELECT * FROM t_order", "t_order"));
        });

        assertNull(DynamicTableNameHolder.get("t_order"));
        assertEquals("t_order", handler.dynamicTableName("SELECT * FROM t_order", "t_order"));
    }

    @Test
    @DisplayName("run 内抛出异常也清理映射")
    void runClearsMappingOnFailure() {
        assertThrows(IllegalStateException.class, () -> DynamicTableNameHolder.run("t_order", "t_order_202402",
                () -> {
                    throw new IllegalStateException("业务失败");
                }));

        assertNull(DynamicTableNameHolder.get("t_order"));
    }

    @Test
    @DisplayName("call 返回结果并清理映射")
    void callReturnsValueAndClearsMapping() {
        String sql = DynamicTableNameHolder.call("t_order", "t_order_202403",
                () -> handler.dynamicTableName("SELECT * FROM t_order", "t_order"));

        assertEquals("t_order_202403", sql);
        assertNull(DynamicTableNameHolder.get("t_order"));
    }

    @Test
    @DisplayName("嵌套作用域退出后恢复外层映射")
    void nestedScopeRestoresOuterMapping() {
        DynamicTableNameHolder.run("t_order", "t_order_outer", () -> {
            DynamicTableNameHolder.run("t_order", "t_order_inner",
                    () -> assertEquals("t_order_inner", DynamicTableNameHolder.get("t_order")));

            assertEquals("t_order_outer", DynamicTableNameHolder.get("t_order"));
        });

        assertNull(DynamicTableNameHolder.get("t_order"));
    }

    @Test
    @DisplayName("嵌套作用域不影响外层的其它表映射")
    void nestedScopeKeepsOtherTables() {
        DynamicTableNameHolder.run("t_order", "t_order_outer", () -> {
            DynamicTableNameHolder.run("t_user", "t_user_2024",
                    () -> assertEquals("t_user_2024", DynamicTableNameHolder.get("t_user")));

            assertEquals("t_order_outer", DynamicTableNameHolder.get("t_order"));
            assertNull(DynamicTableNameHolder.get("t_user"));
        });
    }

    @Test
    @DisplayName("set 与 clear 支持手工管理映射")
    void setUpAndClearControlMapping() {
        DynamicTableNameHolder.set("t_order", "t_order_manual");
        DynamicTableNameHolder.set("t_user", "t_user_manual");
        assertEquals("t_order_manual", DynamicTableNameHolder.get("t_order"));
        assertEquals("t_user_manual", DynamicTableNameHolder.get("t_user"));

        DynamicTableNameHolder.set("t_user", " ");
        assertNull(DynamicTableNameHolder.get("t_user"));

        DynamicTableNameHolder.clear();
        assertNull(DynamicTableNameHolder.get("t_order"));
    }

    @Test
    @DisplayName("空白逻辑表名不写入映射")
    void blankLogicTableIsIgnored() {
        DynamicTableNameHolder.set(" ", "t_order_ignored");
        assertNull(DynamicTableNameHolder.get(" "));
        assertNull(DynamicTableNameHolder.get(null));
    }

    @Test
    @DisplayName("作用域结束后不会把映射泄漏到其它线程")
    void mappingIsNotSharedAcrossThreads() throws InterruptedException {
        AtomicBoolean leaked = new AtomicBoolean(false);
        DynamicTableNameHolder.run("t_order", "t_order_thread", () -> {
            Thread other = new Thread(() -> leaked.set(DynamicTableNameHolder.get("t_order") != null));
            other.start();
            try {
                other.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });

        assertFalse(leaked.get());
        assertNull(DynamicTableNameHolder.get("t_order"));
    }
}
