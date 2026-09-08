package cn.slainc.security.web;

import cn.dev33.satoken.exception.NotLoginException;

public final class SecurityMessages {

    private SecurityMessages() {
    }

    public static String notLoginMessage(NotLoginException e) {
        return notLoginMessage(e == null ? null : e.getType());
    }

    public static String notLoginMessage(String type) {
        return switch (type == null ? "" : type) {
            case NotLoginException.KICK_OUT -> "token 已被踢下线";
            case NotLoginException.BE_REPLACED -> "token 已被顶下线";
            case NotLoginException.TOKEN_TIMEOUT -> "token 已过期";
            case NotLoginException.TOKEN_FREEZE -> "token 已被冻结";
            default -> "未能读取到有效 token";
        };
    }
}
