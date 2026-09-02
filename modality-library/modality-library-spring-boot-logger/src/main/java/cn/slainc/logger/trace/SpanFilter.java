package cn.slainc.logger.trace;

import cn.slainc.logger.autoconfigure.LoggerProperties;
import cn.slainc.logger.constant.LogMdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SpanFilter extends OncePerRequestFilter implements Ordered {

    private final LoggerProperties.Trace properties;

    private final SpanIdGenerator generator;

    private final int order;

    public SpanFilter(LoggerProperties.Trace properties, SpanIdGenerator generator, int order) {
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
        if (!properties.isEnabled() || !properties.isSpanEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String spanId = generator.generate();
        String parentSpanId = normalize(request.getHeader(properties.getSpanHeaderName()));
        MDC.put(LogMdcKeys.SPAN_ID, spanId);
        if (parentSpanId == null) {
            MDC.remove(LogMdcKeys.PARENT_SPAN_ID);
        } else {
            MDC.put(LogMdcKeys.PARENT_SPAN_ID, parentSpanId);
        }
        if (properties.isPropagate()) {
            response.setHeader(properties.getSpanHeaderName(), spanId);
            if (parentSpanId != null) {
                response.setHeader(properties.getParentSpanHeaderName(), parentSpanId);
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(LogMdcKeys.SPAN_ID);
            MDC.remove(LogMdcKeys.PARENT_SPAN_ID);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
