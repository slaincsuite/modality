package cn.slainc.core.lang;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssertAndPreconditionsTest {

    @Test
    void bizAssertThrowsBusinessException() {
        BizAssert.isTrue(true, "不会触发");
        BizException exception = assertThrows(BizException.class, () -> BizAssert.isTrue(false, "条件不成立"));
        assertEquals(ResultCode.BUSINESS_ERROR.code(), exception.code());
        assertEquals("条件不成立", exception.getMessage());

        BizException byCode = assertThrows(BizException.class,
                () -> BizAssert.isTrue(false, ResultCode.FORBIDDEN, "无权限"));
        assertEquals(ResultCode.FORBIDDEN.code(), byCode.code());

        BizAssert.isFalse(false, "不会触发");
        assertThrows(BizException.class, () -> BizAssert.isFalse(true, "必须为假"));
    }

    @Test
    void bizAssertNullAndTextChecks() {
        assertEquals("v", BizAssert.notNull("v", "不能为空"));
        BizException missing = assertThrows(BizException.class, () -> BizAssert.notNull(null, "订单不能为空"));
        assertEquals(ResultCode.PARAM_MISSING.code(), missing.code());

        assertEquals("text", BizAssert.hasText("text", "不能为空"));
        assertThrows(BizException.class, () -> BizAssert.hasText("   ", "不能为空"));
        assertThrows(BizException.class, () -> BizAssert.hasText(null, "不能为空"));
    }

    @Test
    void bizAssertCollectionChecks() {
        assertEquals(List.of(1), BizAssert.notEmpty(List.of(1), "不能为空"));
        assertThrows(BizException.class, () -> BizAssert.notEmpty(List.of(), "不能为空"));
        assertEquals(Map.of("a", 1), BizAssert.notEmpty(Map.of("a", 1), "不能为空"));
        assertThrows(BizException.class, () -> BizAssert.notEmpty(Map.of(), "不能为空"));
    }

    @Test
    void bizAssertBusinessSemantics() {
        BizAssert.found(true, "存在");
        BizException notFound = assertThrows(BizException.class, () -> BizAssert.found(false, "用户不存在"));
        assertEquals(ResultCode.DATA_NOT_FOUND.code(), notFound.code());

        BizAssert.notFound(false, "不存在");
        BizException duplicate = assertThrows(BizException.class, () -> BizAssert.notFound(true, "用户名已存在"));
        assertEquals(ResultCode.DATA_DUPLICATE.code(), duplicate.code());

        BizAssert.state(true, "合法");
        BizException invalid = assertThrows(BizException.class, () -> BizAssert.state(false, "状态不允许"));
        assertEquals(ResultCode.DATA_STATE_INVALID.code(), invalid.code());
    }

    @Test
    void preconditions() {
        Preconditions.checkArgument(true, "格式错误: %s", "x");
        IllegalArgumentException argument = assertThrows(IllegalArgumentException.class,
                () -> Preconditions.checkArgument(false, "订单 %s 不存在", 100L));
        assertEquals("订单 100 不存在", argument.getMessage());

        assertEquals("v", Preconditions.checkNotNull("v", "不能为空"));
        NullPointerException nullPointer = assertThrows(NullPointerException.class,
                () -> Preconditions.checkNotNull(null, "缺少 %s", "tenantId"));
        assertEquals("缺少 tenantId", nullPointer.getMessage());

        Preconditions.checkState(true, "状态错误");
        assertThrows(IllegalStateException.class, () -> Preconditions.checkState(false, "状态错误"));

        assertEquals(1, Preconditions.checkIndex(1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> Preconditions.checkIndex(3, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> Preconditions.checkIndex(-1, 3));
        assertEquals(3, Preconditions.checkPositionIndex(3, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> Preconditions.checkPositionIndex(4, 3));
    }
}
