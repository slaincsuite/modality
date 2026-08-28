package cn.slainc.idempotent.exception;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;

public class DuplicateSubmitException extends BizException {

    public DuplicateSubmitException(String message) {
        super(ResultCode.CONFLICT, message);
    }
}
