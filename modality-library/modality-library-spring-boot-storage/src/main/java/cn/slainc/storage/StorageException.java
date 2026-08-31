package cn.slainc.storage;

import cn.slainc.core.exception.AbstractException;
import cn.slainc.core.exception.ErrorType;
import cn.slainc.core.result.ResultCode;

public class StorageException extends AbstractException {

    public StorageException(String message) {
        super(ResultCode.BUSINESS_ERROR, ErrorType.BUSINESS, message);
    }

    public StorageException(String message, Throwable cause) {
        super(ResultCode.BUSINESS_ERROR, ErrorType.BUSINESS, message, cause);
    }

    public StorageException(ResultCode code, String message, Throwable cause) {
        super(code, ErrorType.SYSTEM, message, cause);
    }
}
