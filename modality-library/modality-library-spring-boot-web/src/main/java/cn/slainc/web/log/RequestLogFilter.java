package cn.slainc.web.log;

import cn.slainc.core.constant.HttpHeaders;
import cn.slainc.core.context.RequestContext;
import cn.slainc.utils.net.IpUtils;
import cn.slainc.utils.string.RegexUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class RequestLogFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    private final RequestLogProperties properties;
    private final int order;

    public RequestLogFilter(RequestLogProperties properties, int order) {
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
        if (!shouldLog(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMillis = (System.nanoTime() - start) / 1_000_000L;
            print(request, response, costMillis);
        }
    }

    private boolean shouldLog(HttpServletRequest request) {
        if (!properties.isEnabled() || matchesExclude(request.getRequestURI())) {
            return false;
        }
        double rate = properties.getSampleRate();
        if (rate >= 1.0d) {
            return true;
        }
        return rate > 0.0d && ThreadLocalRandom.current().nextDouble() < rate;
    }

    private boolean matchesExclude(String path) {
        for (String pattern : properties.getExcludePaths()) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (RegexUtils.matches(pattern.replace("**", ".*").replace("*", "[^/]*"), path)) {
                return true;
            }
        }
        return false;
    }

    private void print(HttpServletRequest request, HttpServletResponse response, long costMillis) {
        String query = SensitiveMasker.maskQueryString(request.getQueryString());
        String clientIp = IpUtils.resolveClientIp(
                request.getHeader(HttpHeaders.FORWARDED_FOR),
                request.getHeader(HttpHeaders.REAL_IP),
                request.getHeader(HttpHeaders.CF_CONNECTING_IP),
                request.getRemoteAddr());
        String target = query == null || query.isBlank()
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + query;
        String traceId = RequestContext.traceId() == null ? "-" : RequestContext.traceId();
        String message = "请求 {} {} | 来源={} | 状态={} | 耗时={}ms | traceId={}";
        if (properties.isLogRequest() && properties.isLogHeaders()) {
            message = message + " | UA=" + SensitiveMasker.truncate(request.getHeader(HttpHeaders.USER_AGENT), 200);
        }
        Object[] args = {request.getMethod(), SensitiveMasker.truncate(target, properties.getMaxPayloadLength()),
                clientIp, response.getStatus(), costMillis, traceId};
        if (costMillis >= properties.getSlowThresholdMillis() || response.getStatus() >= 400) {
            log.warn("慢请求或异常响应 " + message, args);
        } else {
            log.info(message, args);
        }
    }
}
