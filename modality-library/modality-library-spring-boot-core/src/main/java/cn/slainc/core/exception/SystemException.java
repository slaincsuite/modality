package cn.slainc.core.exception;

import cn.slainc.core.result.ErrorCode;
import cn.slainc.core.result.ResultCode;

public class SystemException extends AbstractException {

    public SystemException(String message) {
        this(ResultCode.INTERNAL_SERVER_ERROR, message);
    }

    public SystemException(ErrorCode errorCode) {
        super(errorCode, ErrorType.SYSTEM);
    }

    public SystemException(ErrorCode errorCode, String message) {
        super(errorCode, ErrorType.SYSTEM, message);
    }

    public SystemException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, ErrorType.SYSTEM, message, cause);
    }
}
