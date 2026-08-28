package cn.slainc.idempotent.exception;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;

public class IdempotentRetryExceededException extends BizException {

    public IdempotentRetryExceededException(String message) {
        super(ResultCode.TOO_MANY_REQUESTS, message);
    }
}
