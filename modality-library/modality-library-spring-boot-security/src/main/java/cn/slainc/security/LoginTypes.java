package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;

public final class LoginTypes {

    private LoginTypes() {
    }

    public static LoginType resolve(String raw) {
        LoginType type = LoginType.of(raw);
        if (type == null) {
            throw new BizException("未知的账号体系：" + raw);
        }
        return type;
    }

    public static LoginType resolveOrDefault(String raw, String defaultName) {
        LoginType type = LoginType.of(raw);
        if (type != null) {
            return type;
        }
        LoginType fallback = LoginType.of(defaultName);
        return fallback == null ? LoginType.USER : fallback;
    }
}
