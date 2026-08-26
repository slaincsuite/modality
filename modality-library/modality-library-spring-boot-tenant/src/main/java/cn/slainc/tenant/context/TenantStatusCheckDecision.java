package cn.slainc.tenant.context;

import cn.slainc.core.result.ErrorCode;
import cn.slainc.core.result.ResultCode;

public record TenantStatusCheckDecision(boolean allowed, ErrorCode errorCode, String message) {

    private static final TenantStatusCheckDecision ALLOWED = new TenantStatusCheckDecision(true, null, null);

    public static TenantStatusCheckDecision allow() {
        return ALLOWED;
    }

    public static TenantStatusCheckDecision reject(ErrorCode errorCode, String message) {
        ErrorCode actual = errorCode == null ? ResultCode.FORBIDDEN : errorCode;
        String actualMessage = message == null || message.isBlank() ? actual.message() : message;
        return new TenantStatusCheckDecision(false, actual, actualMessage);
    }

    public int httpStatus() {
        return errorCode == null ? ResultCode.FORBIDDEN.httpStatus() : errorCode.httpStatus();
    }
}
