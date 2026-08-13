package cn.slainc.core.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultCodeTest {

    @Test
    void businessCodeMapsToHttpStatus() {
        assertEquals(200, ResultCode.SUCCESS.httpStatus());
        assertEquals(400, ResultCode.BAD_REQUEST.httpStatus());
        assertEquals(400, ResultCode.PARAM_MISSING.httpStatus());
        assertEquals(401, ResultCode.UNAUTHORIZED.httpStatus());
        assertEquals(403, ResultCode.FORBIDDEN.httpStatus());
        assertEquals(404, ResultCode.NOT_FOUND.httpStatus());
        assertEquals(405, ResultCode.METHOD_NOT_ALLOWED.httpStatus());
        assertEquals(409, ResultCode.CONFLICT.httpStatus());
        assertEquals(415, ResultCode.UNSUPPORTED_MEDIA_TYPE.httpStatus());
        assertEquals(429, ResultCode.TOO_MANY_REQUESTS.httpStatus());
        assertEquals(500, ResultCode.INTERNAL_SERVER_ERROR.httpStatus());
        assertEquals(502, ResultCode.THIRD_PARTY_ERROR.httpStatus());
        assertEquals(503, ResultCode.SERVICE_UNAVAILABLE.httpStatus());
    }

    @Test
    void businessCodesShare400Family() {
        assertEquals(400, ResultCode.BUSINESS_ERROR.httpStatus());
        assertEquals(400, ResultCode.DATA_NOT_FOUND.httpStatus());
        assertEquals(400, ResultCode.DATA_DUPLICATE.httpStatus());
        assertEquals(400, ResultCode.DATA_STATE_INVALID.httpStatus());
    }

    @Test
    void lookupByCode() {
        assertEquals(ResultCode.SUCCESS, ResultCode.of(200));
        assertEquals(ResultCode.DATA_NOT_FOUND, ResultCode.of(400101));
        assertNull(ResultCode.of(999999));
    }

    @Test
    void unknownErrorCodeDefaultsTo500() {
        ErrorCode custom = new ErrorCode() {
            @Override
            public int code() {
                return 7;
            }

            @Override
            public String message() {
                return "自定义错误";
            }
        };
        assertEquals(500, custom.httpStatus());
        assertTrue(ResultCode.SUCCESS instanceof ErrorCode);
    }
}
