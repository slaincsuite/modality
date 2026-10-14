package cn.slainc.oauth2.protocol;

import cn.dev33.satoken.oauth2.exception.SaOAuth2Exception;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class Oauth2ErrorAdvice {

    @ExceptionHandler(SaOAuth2Exception.class)
    public Result<Void> handle(SaOAuth2Exception e) {
        return Result.fail(ResultCode.BAD_REQUEST.code(), e.getMessage());
    }
}
