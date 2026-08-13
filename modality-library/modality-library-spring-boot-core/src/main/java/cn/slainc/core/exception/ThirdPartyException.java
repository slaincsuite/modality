package cn.slainc.core.exception;

import cn.slainc.core.result.ErrorCode;
import cn.slainc.core.result.ResultCode;

public class ThirdPartyException extends AbstractException {

    private String service;

    public ThirdPartyException(String message) {
        this(ResultCode.THIRD_PARTY_ERROR, message);
    }

    public ThirdPartyException(ErrorCode errorCode, String message) {
        super(errorCode, ErrorType.THIRD_PARTY, message);
    }

    public ThirdPartyException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, ErrorType.THIRD_PARTY, message, cause);
    }

    public ThirdPartyException withService(String serviceName) {
        this.service = serviceName;
        withContext("service", serviceName);
        return this;
    }

    public String service() {
        return service;
    }
}
