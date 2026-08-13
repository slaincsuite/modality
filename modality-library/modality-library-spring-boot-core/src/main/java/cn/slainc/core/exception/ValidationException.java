package cn.slainc.core.exception;

import cn.slainc.core.result.ErrorCode;
import cn.slainc.core.result.ResultCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationException extends AbstractException {

    private final List<String> violations = new ArrayList<>();

    public ValidationException(String message) {
        this(ResultCode.BAD_REQUEST, message);
    }

    public ValidationException(ErrorCode errorCode, String message) {
        super(errorCode, ErrorType.VALIDATION, message);
    }

    public ValidationException(ErrorCode errorCode, String message, List<String> violations) {
        super(errorCode, ErrorType.VALIDATION, message);
        if (violations != null) {
            this.violations.addAll(violations);
        }
    }

    public List<String> violations() {
        return Collections.unmodifiableList(violations);
    }

    public ValidationException addViolation(String violation) {
        if (violation != null) {
            violations.add(violation);
        }
        return this;
    }
}
