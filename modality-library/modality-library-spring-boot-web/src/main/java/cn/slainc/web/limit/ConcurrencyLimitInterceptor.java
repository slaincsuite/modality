package cn.slainc.web.limit;

import cn.slainc.core.context.RequestContext;
import cn.slainc.core.exception.SystemException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.utils.string.RegexUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyLimitInterceptor implements HandlerInterceptor {

    private final RequestLimitProperties properties;
    private final Map<String, AtomicInteger> perIpCounters = new ConcurrentHashMap<>();
    private final AtomicInteger globalCounter = new AtomicInteger();

    public ConcurrencyLimitInterceptor(RequestLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled() || matchesExclude(request.getRequestURI())) {
            return true;
        }
        int global = globalCounter.incrementAndGet();
        if (global > properties.getMaxConcurrentGlobal()) {
            globalCounter.decrementAndGet();
            throw new SystemException(ResultCode.TOO_MANY_REQUESTS, properties.getMessage());
        }
        String ip = resolveIp();
        AtomicInteger counter = perIpCounters.computeIfAbsent(ip, key -> new AtomicInteger());
        int current = counter.incrementAndGet();
        if (current > properties.getMaxConcurrentPerIp()) {
            counter.decrementAndGet();
            globalCounter.decrementAndGet();
            throw new SystemException(ResultCode.TOO_MANY_REQUESTS, properties.getMessage());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        if (!properties.isEnabled() || matchesExclude(request.getRequestURI())) {
            return;
        }
        String ip = resolveIp();
        AtomicInteger counter = perIpCounters.get(ip);
        if (counter != null && counter.decrementAndGet() <= 0) {
            perIpCounters.remove(ip, counter);
        }
        globalCounter.decrementAndGet();
    }

    public int currentGlobal() {
        return globalCounter.get();
    }

    private String resolveIp() {
        String ip = RequestContext.clientIp();
        return ip == null ? "unknown" : ip;
    }

    private boolean matchesExclude(String path) {
        for (String pattern : properties.getExcludePaths()) {
            if (pattern != null && !pattern.isBlank()
                    && RegexUtils.matches(pattern.replace("**", ".*").replace("*", "[^/]*"), path)) {
                return true;
            }
        }
        return false;
    }
}
