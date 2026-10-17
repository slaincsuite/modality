package cn.slainc.modules.infra.support;

import cn.slainc.apikey.config.ApiKeyProperties;
import cn.slainc.modules.infra.store.ApiKeyRequestMetricsStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class ApiKeyMetricsFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyMetricsFilter.class);

    private final ApiKeyProperties properties;

    private final ApiKeyRequestMetricsStore metricsStore;

    public ApiKeyMetricsFilter(ApiKeyProperties properties, ApiKeyRequestMetricsStore metricsStore) {
        this.properties = properties;
        this.metricsStore = metricsStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(properties.getHeader());
        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                metricsStore.record(apiKey, response.getStatus(), (System.nanoTime() - start) / 1_000_000L);
            } catch (RuntimeException exception) {
                log.warn("API Key 调用指标写入失败，原因={}", exception.getMessage());
            }
        }
    }
}
