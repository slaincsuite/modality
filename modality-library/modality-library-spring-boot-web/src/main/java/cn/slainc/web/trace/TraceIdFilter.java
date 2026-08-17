package cn.slainc.web.trace;

import cn.slainc.core.constant.HttpHeaders;
import cn.slainc.core.constant.SystemConstants;
import cn.slainc.core.context.RequestContext;
import cn.slainc.utils.net.IpUtils;
import cn.slainc.web.config.TraceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TraceIdFilter extends OncePerRequestFilter implements Ordered {

    private final TraceProperties properties;
    private final TraceIdGenerator generator;
    private final int order;

    public TraceIdFilter(TraceProperties properties, TraceIdGenerator generator, int order) {
        this.properties = properties;
        this.generator = generator;
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(SystemConstants.TRACE_ID_MDC_KEY, traceId);
        RequestContext.set(new RequestContext.RequestInfo(traceId, resolveClientIp(request),
                request.getHeader(HttpHeaders.USER_AGENT), request.getMethod(), request.getRequestURI()));
        response.setHeader(properties.getHeaderName(), traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.clear();
            MDC.remove(SystemConstants.TRACE_ID_MDC_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        if (properties.isAcceptUpstream()) {
            String upstream = request.getHeader(properties.getHeaderName());
            if (upstream == null || upstream.isBlank()) {
                upstream = request.getHeader(HttpHeaders.REQUEST_ID);
            }
            if (upstream != null && !upstream.isBlank()) {
                return upstream.trim();
            }
        }
        return generator.generate();
    }

    private String resolveClientIp(HttpServletRequest request) {
        return IpUtils.resolveClientIp(
                request.getHeader(HttpHeaders.FORWARDED_FOR),
                request.getHeader(HttpHeaders.REAL_IP),
                request.getHeader(HttpHeaders.CF_CONNECTING_IP),
                request.getRemoteAddr());
    }
}
