package cn.slainc.core.result;

public record Result<T>(int code, String message, T data, long timestamp) {

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.code(), ResultCode.SUCCESS.message(), data,
                System.currentTimeMillis());
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode == null ? null : errorCode.message());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        int code = errorCode == null ? ResultCode.INTERNAL_SERVER_ERROR.code() : errorCode.code();
        String actualMessage = message == null || message.isBlank()
                ? (errorCode == null ? ResultCode.INTERNAL_SERVER_ERROR.message() : errorCode.message())
                : message;
        return new Result<>(code, actualMessage, null, System.currentTimeMillis());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }

    public boolean isSuccess() {
        return code == ResultCode.SUCCESS.code();
    }

    public int httpStatus() {
        if (isSuccess()) {
            return ResultCode.SUCCESS.httpStatus();
        }
        ResultCode rc = ResultCode.of(code);
        return rc == null ? 500 : rc.httpStatus();
    }

    public Result<T> withMessage(String newMessage) {
        return new Result<>(code, newMessage, data, timestamp);
    }
}
