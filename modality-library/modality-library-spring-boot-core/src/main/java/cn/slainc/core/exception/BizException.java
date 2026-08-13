package cn.slainc.core.exception;

import cn.slainc.core.result.ErrorCode;
import cn.slainc.core.result.ResultCode;

public class BizException extends AbstractException {

    public BizException(String message) {
        this(ResultCode.BUSINESS_ERROR, message);
    }

    public BizException(ErrorCode errorCode) {
        super(errorCode, ErrorType.BUSINESS);
    }

    public BizException(ErrorCode errorCode, String message) {
        super(errorCode, ErrorType.BUSINESS, message);
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, ErrorType.BUSINESS, message, cause);
    }
}
