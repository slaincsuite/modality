package cn.slainc.security.web;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.NotSafeException;
import cn.slainc.core.result.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleNotLogin(NotLoginException e) {
        return Result.fail(401000, SecurityMessages.notLoginMessage(e));
    }

    @ExceptionHandler(NotRoleException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotRole(NotRoleException e) {
        return Result.fail(403000, "无此角色：" + e.getRole());
    }

    @ExceptionHandler(NotPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotPermission(NotPermissionException e) {
        return Result.fail(403000, "无此权限：" + e.getPermission());
    }

    @ExceptionHandler(NotSafeException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleNotSafe(NotSafeException e) {
        String service = e.getService() == null ? "" : "（" + e.getService() + "）";
        return Result.fail(403000, "二级认证校验未通过，请先完成二次验证" + service);
    }

    @ExceptionHandler(DisableServiceException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleDisable(DisableServiceException e) {
        String service = e.getService() == null ? "" : String.valueOf(e.getService());
        return Result.fail(403000, service.isBlank() ? "账号已被封禁" : "账号已被封禁：" + service);
    }

    @ExceptionHandler(SecurityForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleSecurityForbidden(SecurityForbiddenException e) {
        return Result.fail(403000, e.getMessage());
    }
}
