package cn.slainc.apikey.advice;

import cn.dev33.satoken.apikey.exception.ApiKeyException;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyErrorAdvice {

    @ExceptionHandler(ApiKeyException.class)
    public Result<Void> handle(ApiKeyException e) {
        return Result.fail(ResultCode.UNAUTHORIZED.code(), e.getMessage());
    }
}
