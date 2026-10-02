package cn.slainc.modules.infra.context;

import cn.slainc.core.context.UserContext;
import cn.slainc.security.StpKit;

// 操作者信息以 upms 的上下文绑定过滤器写入的 UserContext 为准（boot 全量环境）；无 upms 时按默认体系登录态兜底
public class CurrentUser {

    public String operator() {
        UserContext.UserInfo user = UserContext.get();
        if (user != null) {
            if (user.username() != null && !user.username().isBlank()) {
                return user.username();
            }
            if (user.userId() != null) {
                return String.valueOf(user.userId());
            }
        }
        Long loginId = userId();
        return loginId == null ? "系统" : String.valueOf(loginId);
    }

    public Long userId() {
        UserContext.UserInfo user = UserContext.get();
        if (user != null && user.userId() != null) {
            return user.userId();
        }
        Object loginId = StpKit.tenantUser().getLoginIdDefaultNull();
        if (loginId == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(loginId));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public String loginType() {
        UserContext.UserInfo user = UserContext.get();
        return user == null || user.loginType() == null ? "TENANT_USER" : user.loginType().name();
    }
}
