package cn.slainc.logger.context;

import cn.slainc.core.constant.SystemConstants;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.context.UserContext;
import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.constant.LogHeaders;
import cn.slainc.logger.constant.LogMdcKeys;
import cn.slainc.core.context.RequestContext;
import cn.slainc.web.log.SensitiveMasker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public class LogContextFilter extends OncePerRequestFilter implements Ordered {

    private final LoggerProperties.Context properties;

    private final LoginIdResolver loginIdResolver;

    private final int order;

    public LogContextFilter(LoggerProperties.Context properties, LoginIdResolver loginIdResolver, int order) {
        this.properties = properties;
        this.loginIdResolver = loginIdResolver;
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        Set<String> injected = new LinkedHashSet<>();
        put(injected, LogMdcKeys.CLIENT_IP, RequestContext.clientIp());
        put(injected, LogMdcKeys.USER_AGENT,
                SensitiveMasker.truncate(RequestContext.userAgent(), properties.getMaxUserAgentLength()));
        put(injected, LogMdcKeys.REQUEST_PATH, request.getRequestURI());
        put(injected, LogMdcKeys.HTTP_METHOD, request.getMethod());
        put(injected, LogMdcKeys.DEVICE_ID, request.getHeader(LogHeaders.DEVICE_ID));
        if (properties.isInjectTenant()) {
            put(injected, SystemConstants.TENANT_ID_MDC_KEY, toText(TenantContext.get()));
        }
        if (properties.isInjectUser()) {
            put(injected, SystemConstants.USER_ID_MDC_KEY, toText(UserContext.userId()));
            put(injected, LogMdcKeys.USERNAME, UserContext.username());
            put(injected, LogMdcKeys.LOGIN_ID, loginIdResolver == null ? null : loginIdResolver.resolveLoginId());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            for (String key : injected) {
                MDC.remove(key);
            }
        }
    }

    private void put(Set<String> injected, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        MDC.put(key, value);
        injected.add(key);
    }

    private String toText(Long value) {
        return value == null ? null : Long.toString(value);
    }
}
