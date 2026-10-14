package cn.slainc.modules.auth.service.mfa;

import cn.slainc.core.enums.LoginType;

// 本接口不引用 mfa 库类型：未引入二次验证库时登录链路可安全装载并按缺省直通
public interface AuthSecondFactorGate {

    boolean appliesTo(LoginType type);

    boolean required(LoginType type, Long userId, String username);

    String challenge(LoginType type, Long userId, String username, String deviceType, String deviceId,
                     boolean remember);

    String method();
}
