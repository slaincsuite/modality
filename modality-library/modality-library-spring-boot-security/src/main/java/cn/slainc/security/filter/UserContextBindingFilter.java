package cn.slainc.security.filter;

import cn.dev33.satoken.stp.StpInterface;
import cn.slainc.core.context.UserContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

// 从请求头解析的账号体系读取当前会话，绑定 UserContext 供数据权限等下游消费；请求结束必须清理，防止线程池串数据
public class UserContextBindingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(UserContextBindingFilter.class);

    private final ObjectProvider<StpInterface> stpInterface;

    public UserContextBindingFilter(ObjectProvider<StpInterface> stpInterface) {
        this.stpInterface = stpInterface;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            bind(request);
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private void bind(ServletRequest request) {
        if (!(request instanceof HttpServletRequest httpRequest)) {
            return;
        }
        String typeValue = httpRequest.getHeader("X-Login-Type");
        LoginType type = LoginType.of(typeValue == null || typeValue.isBlank() ? LoginType.TENANT_USER.value()
                : typeValue);
        if (type == null) {
            return;
        }
        try {
            Object loginId = StpKit.of(type).getLoginIdDefaultNull();
            if (loginId == null) {
                return;
            }
            Long userId;
            try {
                userId = Long.valueOf(String.valueOf(loginId));
            } catch (NumberFormatException e) {
                return;
            }
            StpInterface resolved = stpInterface.getIfAvailable();
            if (resolved == null) {
                return;
            }
            List<String> roles = resolved.getRoleList(loginId, type.value());
            List<String> permissions = resolved.getPermissionList(loginId, type.value());
            UserContext.set(new UserContext.UserInfo(userId, null, type,
                    new LinkedHashSet<>(roles == null ? List.of() : roles),
                    new LinkedHashSet<>(permissions == null ? List.of() : permissions),
                    type == LoginType.PLATFORM_ADMIN));
        } catch (Exception e) {
            // 上下文绑定失败不阻断请求，下游按未登录语义处理
            log.debug("用户上下文绑定失败: {}", e.getMessage());
        }
    }
}
