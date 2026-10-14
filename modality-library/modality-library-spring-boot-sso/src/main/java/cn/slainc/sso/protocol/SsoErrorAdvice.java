package cn.slainc.sso.protocol;

import cn.dev33.satoken.sso.exception.SaSsoException;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SsoErrorAdvice {

    @ExceptionHandler(SaSsoException.class)
    public Result<Void> handle(SaSsoException e) {
        return Result.fail(ResultCode.BAD_REQUEST.code(), e.getMessage());
    }
}
