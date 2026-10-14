package cn.slainc.modules.upms.service;

import cn.slainc.core.context.UserContext;
import cn.slainc.core.exception.BizException;

import java.util.Locale;

// 我的收藏与最近访问共用：当前账号解析与目标标识归一，自助数据一律落在登录账号上
final class SelfServiceSupport {

    private SelfServiceSupport() {
    }

    static Long currentUserId() {
        Long userId = UserContext.userId();
        if (userId == null) {
            throw new BizException("登录状态已失效，请重新登录");
        }
        return userId;
    }

    static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }

    static String targetType(String value) {
        return requireText(value, "目标类型不能为空").toUpperCase(Locale.ROOT);
    }
}
