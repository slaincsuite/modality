package cn.slainc.core.exception;

import cn.slainc.core.result.ResultCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionTest {

    @Test
    void bizException() {
        BizException exception = new BizException("业务失败");
        assertEquals(ResultCode.BUSINESS_ERROR.code(), exception.code());
        assertEquals(ErrorType.BUSINESS, exception.errorType());
        assertEquals(400, exception.httpStatus());
        assertEquals("业务失败", exception.getMessage());

        BizException byCode = new BizException(ResultCode.DATA_NOT_FOUND);
        assertEquals(400101, byCode.code());
        assertEquals("数据不存在", byCode.getMessage());
    }

    @Test
    void exceptionContextIsImmutable() {
        BizException exception = new BizException("x");
        exception.withContext("orderId", 100L).withContext(Map.of("channel", "app"));
        assertEquals(100L, exception.context().get("orderId"));
        assertEquals("app", exception.context().get("channel"));
        assertThrows(UnsupportedOperationException.class, () -> exception.context().put("k", "v"));
        assertEquals(ResultCode.BUSINESS_ERROR.code(), exception.code());
    }

    @Test
    void systemExceptionDefaultsTo500() {
        SystemException exception = new SystemException("系统异常");
        assertEquals(ResultCode.INTERNAL_SERVER_ERROR.code(), exception.code());
        assertEquals(ErrorType.SYSTEM, exception.errorType());
        assertEquals(500, exception.httpStatus());
    }

    @Test
    void thirdPartyExceptionCarriesService() {
        ThirdPartyException exception = new ThirdPartyException("远程调用失败");
        assertEquals(ErrorType.THIRD_PARTY, exception.errorType());
        assertEquals(502, exception.httpStatus());
        assertNull(exception.service());
        assertEquals("payment", exception.withService("payment").service());
        assertEquals("payment", exception.context().get("service"));
    }

    @Test
    void validationExceptionCollectsViolations() {
        ValidationException exception = new ValidationException(ResultCode.BAD_REQUEST, "参数校验失败",
                List.of("name 不能为空", "age 必须大于 0"));
        assertEquals(ErrorType.VALIDATION, exception.errorType());
        assertEquals(2, exception.violations().size());
        assertEquals(3, exception.addViolation("email 格式错误").violations().size());
        assertThrows(UnsupportedOperationException.class, () -> exception.violations().add("x"));
    }

    @Test
    void causeChainIsTraceable() {
        IllegalStateException root = new IllegalStateException("根因");
        BizException exception = new BizException(ResultCode.BUSINESS_ERROR, "外层失败", root);
        assertSame(root, exception.getCause());
        assertSame(root, ExceptionUtils.getRootCause(exception));
        assertEquals("根因", ExceptionUtils.getMessage(exception));
        assertEquals(2, ExceptionUtils.getCauseChain(exception).size());
        assertEquals("BizException: 外层失败 <- IllegalStateException: 根因", ExceptionUtils.buildMessage(exception));
    }

    @Test
    void handlesNullAndCyclicCauses() {
        assertNull(ExceptionUtils.getRootCause(null));
        assertNull(ExceptionUtils.getMessage(null));
        assertNull(ExceptionUtils.buildMessage(null));
        assertNull(ExceptionUtils.trim(null));
        assertTrue(ExceptionUtils.getCauseChain(null).isEmpty());

        RuntimeException first = new RuntimeException("a");
        RuntimeException second = new RuntimeException("b", first);
        first.initCause(second);
        assertEquals(2, ExceptionUtils.getCauseChain(first).size());
    }

    @Test
    void trimsFrameworkFrames() {
        BizException exception = new BizException("x");
        int before = exception.getStackTrace().length;
        ExceptionUtils.trim(exception, "org.junit.");
        assertTrue(exception.getStackTrace().length <= before);
        for (StackTraceElement element : exception.getStackTrace()) {
            assertFalse(element.getClassName().startsWith("org.junit."));
        }
        assertEquals(before, ExceptionUtils.trim(new BizException("y"), "不存在的包名前缀").getStackTrace().length);
    }
}
