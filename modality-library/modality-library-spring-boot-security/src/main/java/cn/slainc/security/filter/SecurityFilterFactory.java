package cn.slainc.security.filter;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.Result;
import cn.slainc.security.LoginTypes;
import cn.slainc.security.StpKit;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.security.handler.SecurityForbiddenException;
import cn.slainc.security.handler.SecurityMessages;
import cn.slainc.security.config.SecurityProperties;
import cn.slainc.security.metrics.SecurityMetricsPort;
import cn.slainc.utils.json.JsonUtils;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import cn.slainc.web.filter.FilterOrder;

import jakarta.servlet.Filter;

import java.net.URI;
import java.util.List;

public final class SecurityFilterFactory {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityFilterFactory.class);

    private SecurityFilterFactory() {
    }

    private static String currentRequestPath() {
        try {
            return SaHolder.getRequest().getRequestPath();
        } catch (Exception e) {
            return null;
        }
    }

    public static FilterRegistrationBean<Filter> create(SecurityProperties properties,
                                                                 SecurityConfigReader config,
                                                                 SecurityMetricsPort metrics) {
        SaServletFilter filter = new SaServletFilter();
        // 白名单与保护范围必须按请求实时读取才能被热配置改到；Sa-Token 的 include/exclude 只在建 Filter 时取一次值
        filter.addInclude("/**");
        LoginTypeResolver typeResolver = new LoginTypeResolver(config);
        OriginValidator originValidator = new OriginValidator(config);
        filter.setBeforeAuth(r -> {
            LoginType type = typeResolver.resolve();
            List<String> blacklist = config.filterBlacklistPatterns();
            if (blacklist != null && !blacklist.isEmpty()) {
                cn.dev33.satoken.router.SaRouter.match(blacklist)
                        .check(item -> {
                            metrics.authorization(false);
                            throw new SecurityForbiddenException("请求路径已被列入黑名单，禁止访问");
                        });
            }
            originValidator.check();
        });
        filter.setAuth(r -> {
            String path = currentRequestPath();
            if (path != null) {
                if (cn.dev33.satoken.router.SaRouter.isMatch(config.filterExcludePatterns(), path)) {
                    return;
                }
                if (!cn.dev33.satoken.router.SaRouter.isMatch(config.filterProtectedPatterns(), path)) {
                    return;
                }
            }
            LoginType type = typeResolver.resolve();
            try {
                StpKit.of(type).checkLogin();
                metrics.authorization(true);
            } catch (NotLoginException e) {
                metrics.authorization(false);
                throw e;
            }
        });
        filter.setError(e -> ErrorResponder.respond(e));
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(
                new SaTokenContextBindingFilter(filter));
        registration.setOrder(FilterOrder.SA_TOKEN);
        registration.addUrlPatterns("/*");
        registration.setName("securitySaServletFilter");
        registration.setAsyncSupported(true);
        return registration;
    }

    static final class LoginTypeResolver {

        private final SecurityConfigReader config;

        LoginTypeResolver(SecurityConfigReader config) {
            this.config = config;
        }

        LoginType resolve() {
            String headerName = config.typeHeader();
            String raw = null;
            try {
                raw = SaHolder.getRequest().getHeader(headerName);
            } catch (Exception ignored) {
                return defaultType();
            }
            return LoginTypes.resolveOrDefault(raw, config.defaultTypeName());
        }

        LoginType defaultType() {
            return LoginTypes.resolveOrDefault(null, config.defaultTypeName());
        }
    }

    static final class OriginValidator {

        private final SecurityConfigReader config;

        OriginValidator(SecurityConfigReader config) {
            this.config = config;
        }

        void check() {
            if (!config.originEnabled()) {
                return;
            }
            String origin;
            try {
                origin = SaHolder.getRequest().getHeader("Origin");
            } catch (Exception e) {
                return;
            }
            if (origin == null || origin.isBlank() || "null".equalsIgnoreCase(origin.trim())) {
                return;
            }
            List<String> allowed = config.originAllowedOrigins();
            if (allowed != null && allowed.contains(origin.trim())) {
                return;
            }
            if (isSameOrigin(origin.trim())) {
                return;
            }
            throw new SecurityForbiddenException("请求来源不被允许：Origin 与站点不同源且不在允许列表中");
        }

        private boolean isSameOrigin(String origin) {
            try {
                URI uri = URI.create(origin);
                String host = SaHolder.getRequest().getHeader("Host");
                if (host == null || uri.getHost() == null) {
                    return false;
                }
                if (host.equalsIgnoreCase(uri.getHost())) {
                    return true;
                }
                int port = uri.getPort();
                return port > 0 && host.equalsIgnoreCase(uri.getHost() + ":" + port);
            } catch (Exception e) {
                return false;
            }
        }
    }

    static final class ErrorResponder {

        static String respond(Throwable e) {
            Result<Void> result;
            int status;
            if (e instanceof NotLoginException nle) {
                result = Result.fail(401000, SecurityMessages.notLoginMessage(nle));
                status = 401;
            } else if (e instanceof SecurityForbiddenException sfe) {
                result = Result.fail(403000, sfe.getMessage());
                status = 403;
            } else {
                log.warn("认证过滤器捕获未预期异常，已返回统一错误响应", e);
                result = Result.fail(500000, "认证服务内部错误，请稍后重试");
                status = 500;
            }
            try {
                SaHolder.getResponse().setHeader("Content-Type", "application/json;charset=UTF-8")
                        .setStatus(status);
            } catch (Exception ignored) {
            }
            return JsonUtils.toJson(result);
        }

    }
}
