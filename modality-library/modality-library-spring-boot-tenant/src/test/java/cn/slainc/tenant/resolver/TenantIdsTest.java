package cn.slainc.tenant.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantIdsTest {

    private final TenantLookup lookup = Map.of("acme", 5L, "shop", 6L)::get;

    @Test
    @DisplayName("数字文本直接解析为租户 ID")
    void numericTextIsTenantId() {
        assertEquals(12L, TenantIds.parse("12", lookup));
        assertEquals(12L, TenantIds.parse(" 12 ", lookup));
        assertEquals(0L, TenantIds.parse("0", lookup));
    }

    @Test
    @DisplayName("非数字文本按租户编码查询")
    void textIsResolvedByCode() {
        assertEquals(5L, TenantIds.parse("acme", lookup));
        assertEquals(6L, TenantIds.parse("shop", lookup));
    }

    @Test
    @DisplayName("空值、未知编码与缺少查询器时返回 null")
    void blankAndUnknownReturnNull() {
        assertNull(TenantIds.parse(null, lookup));
        assertNull(TenantIds.parse("   ", lookup));
        assertNull(TenantIds.parse("unknown", lookup));
        assertNull(TenantIds.parse("acme", null), "没有查询器时无法解析编码");
    }

    @Test
    @DisplayName("parseNumeric 只接受纯数字")
    void parseNumericRejectsNonDigits() {
        assertEquals(8L, TenantIds.parseNumeric("8"));
        assertNull(TenantIds.parseNumeric("8a"));
        assertNull(TenantIds.parseNumeric("-1"));
        assertNull(TenantIds.parseNumeric("99999999999999999999"));
        assertNull(TenantIds.parseNumeric(null));
    }
}
