package cn.slainc.web.header;

import cn.slainc.core.constant.HttpHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SecurityHeadersFilter extends OncePerRequestFilter implements Ordered {

    private final SecurityHeadersProperties properties;
    private final int order;

    public SecurityHeadersFilter(SecurityHeadersProperties properties, int order) {
        this.properties = properties;
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        apply(request, response);
        filterChain.doFilter(request, response);
    }

    private void apply(HttpServletRequest request, HttpServletResponse response) {
        if (properties.getContentTypeOptions() != null) {
            response.setHeader("X-Content-Type-Options", properties.getContentTypeOptions());
        }
        if (properties.getFrameOptions() != null) {
            response.setHeader("X-Frame-Options", properties.getFrameOptions());
        }
        if (properties.isHstsEnabled() && isSecure(request)) {
            StringBuilder hsts = new StringBuilder("max-age=").append(properties.getHstsMaxAge());
            if (properties.isHstsIncludeSubDomains()) {
                hsts.append("; includeSubDomains");
            }
            response.setHeader("Strict-Transport-Security", hsts.toString());
        }
        if (properties.getContentSecurityPolicy() != null && !properties.getContentSecurityPolicy().isBlank()) {
            response.setHeader("Content-Security-Policy", properties.getContentSecurityPolicy());
        }
        if (properties.getReferrerPolicy() != null) {
            response.setHeader("Referrer-Policy", properties.getReferrerPolicy());
        }
        if (properties.getPermissionsPolicy() != null) {
            response.setHeader("Permissions-Policy", properties.getPermissionsPolicy());
        }
    }

    private boolean isSecure(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader(HttpHeaders.FORWARDED_PROTO);
        return forwardedProto != null && forwardedProto.equalsIgnoreCase("https");
    }
}
