package cn.slainc.core.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultTest {

    @Test
    void successResult() {
        Result<String> result = Result.ok("data");
        assertEquals(200, result.code());
        assertEquals("成功", result.message());
        assertEquals("data", result.data());
        assertTrue(result.isSuccess());
        assertEquals(200, result.httpStatus());
        assertTrue(result.timestamp() > 0L);
        assertNull(Result.ok().data());
    }

    @Test
    void failureFromErrorCode() {
        Result<Void> result = Result.fail(ResultCode.DATA_NOT_FOUND);
        assertEquals(400101, result.code());
        assertEquals("数据不存在", result.message());
        assertFalse(result.isSuccess());
        assertEquals(400, result.httpStatus());
        assertNull(result.data());
    }

    @Test
    void failureWithCustomMessage() {
        Result<Object> result = Result.fail(ResultCode.BAD_REQUEST, "订单号不能为空");
        assertEquals(400000, result.code());
        assertEquals("订单号不能为空", result.message());
        assertEquals(ResultCode.BAD_REQUEST.message(), Result.fail(ResultCode.BAD_REQUEST, "  ").message());
    }

    @Test
    void failureWithRawCode() {
        Result<Object> result = Result.fail(400002, "参数类型不匹配");
        assertEquals(400002, result.code());
        assertEquals("参数类型不匹配", result.message());
        assertEquals(400, result.httpStatus());
    }

    @Test
    void nullErrorCodeFallsBackToServerError() {
        Result<Object> result = Result.fail((ErrorCode) null);
        assertEquals(ResultCode.INTERNAL_SERVER_ERROR.code(), result.code());
        assertEquals(ResultCode.INTERNAL_SERVER_ERROR.message(), result.message());
        assertEquals(500, result.httpStatus());
    }

    @Test
    void unknownCodeMapsTo500() {
        Result<Object> result = Result.fail(987654, "未知错误");
        assertEquals(987654, result.code());
        assertEquals(500, result.httpStatus());
    }

    @Test
    void withMessageKeepsCodeAndData() {
        Result<String> changed = Result.ok("data").withMessage("已处理");
        assertEquals(200, changed.code());
        assertEquals("已处理", changed.message());
        assertEquals("data", changed.data());
    }
}
