package cn.slainc.monitor.metrics;

import cn.slainc.web.filter.FilterOrder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class HttpConcurrencyFilter extends OncePerRequestFilter implements Ordered {

    private final HttpConcurrencyMetrics metrics;

    public HttpConcurrencyFilter(HttpConcurrencyMetrics metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("HttpConcurrencyMetrics 不能为空");
        }
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        metrics.onRequestStart();
        try {
            chain.doFilter(request, response);
        } finally {
            metrics.onResponse(response.getStatus());
            metrics.onRequestFinish();
        }
    }

    @Override
    public int getOrder() {
        return FilterOrder.TRACE + 10;
    }
}
