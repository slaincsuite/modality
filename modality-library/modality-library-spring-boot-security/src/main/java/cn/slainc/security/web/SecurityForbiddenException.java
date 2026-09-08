package cn.slainc.security.web;

import cn.dev33.satoken.exception.SaTokenException;

public class SecurityForbiddenException extends SaTokenException {

    public SecurityForbiddenException(String message) {
        super(message);
    }
}
