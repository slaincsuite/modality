package cn.slainc.modules.auth.login;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.LoginTypes;
import cn.slainc.security.StpKit;
import jakarta.servlet.http.HttpServletRequest;

public final class CurrentLogin {

    public static final String TYPE_HEADER = "X-Login-Type";

    private CurrentLogin() {
    }

    public static LoginType type(String headerValue) {
        return LoginTypes.resolveOrDefault(headerValue, LoginType.TENANT_USER.value());
    }

    public static LoginType type(HttpServletRequest request) {
        return type(request.getHeader(TYPE_HEADER));
    }

    public static void requireLogin(HttpServletRequest request) {
        StpKit.of(type(request)).checkLogin();
    }

    // Sa-Token 存储里的 loginId 读回是字符串，比较与解析统一走这里
    public static Long userId(Object loginId) {
        if (loginId instanceof Number number) {
            return number.longValue();
        }
        if (loginId != null) {
            try {
                return Long.parseLong(loginId.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
