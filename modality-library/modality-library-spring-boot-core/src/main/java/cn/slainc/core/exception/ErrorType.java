package cn.slainc.core.exception;

import cn.slainc.core.result.ErrorCode;

public enum ErrorType implements ErrorCode {

    BUSINESS(400100, "业务异常"),
    VALIDATION(400003, "参数校验异常"),
    SYSTEM(500000, "系统异常"),
    THIRD_PARTY(502000, "第三方服务异常");

    private final int code;
    private final String message;

    ErrorType(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
