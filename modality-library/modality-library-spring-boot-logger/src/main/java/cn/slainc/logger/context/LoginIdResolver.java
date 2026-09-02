package cn.slainc.logger.context;

import cn.slainc.core.context.UserContext;

@FunctionalInterface
public interface LoginIdResolver {

    LoginIdResolver USER_CONTEXT = () -> {
        Long userId = UserContext.userId();
        return userId == null ? null : Long.toString(userId);
    };

    String resolveLoginId();
}
