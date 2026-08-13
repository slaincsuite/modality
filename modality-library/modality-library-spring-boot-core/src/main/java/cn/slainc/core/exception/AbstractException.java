package cn.slainc.core.exception;

import cn.slainc.core.result.ErrorCode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractException extends RuntimeException {

    private final ErrorCode errorCode;
    private final ErrorType errorType;
    private final Map<String, Object> context = new LinkedHashMap<>();

    protected AbstractException(ErrorCode errorCode, ErrorType errorType) {
        this(errorCode, errorType, errorCode == null ? null : errorCode.message(), null);
    }

    protected AbstractException(ErrorCode errorCode, ErrorType errorType, String message) {
        this(errorCode, errorType, message, null);
    }

    protected AbstractException(ErrorCode errorCode, ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorType = errorType;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public ErrorType errorType() {
        return errorType;
    }

    public int code() {
        return errorCode == null ? errorType.code() : errorCode.code();
    }

    public int httpStatus() {
        return errorCode == null ? errorType.httpStatus() : errorCode.httpStatus();
    }

    public AbstractException withContext(String key, Object value) {
        if (key != null) {
            context.put(key, value);
        }
        return this;
    }

    public AbstractException withContext(Map<String, Object> values) {
        if (values != null) {
            context.putAll(values);
        }
        return this;
    }

    public Map<String, Object> context() {
        return Collections.unmodifiableMap(context);
    }
}
