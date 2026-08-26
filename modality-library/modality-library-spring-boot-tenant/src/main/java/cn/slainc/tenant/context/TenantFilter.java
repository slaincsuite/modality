package cn.slainc.tenant.context;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.tenant.metrics.TenantMetricsRecorder;
import cn.slainc.tenant.resolver.TenantResolver;
import cn.slainc.utils.json.JsonUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TenantFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    private final TenantResolver resolver;

    private final TenantMetricsRecorder metrics;

    private final TenantStatusChecker statusChecker;

    public TenantFilter(TenantResolver resolver) {
        this(resolver, null, null);
    }

    public TenantFilter(TenantResolver resolver, TenantMetricsRecorder metrics) {
        this(resolver, metrics, null);
    }

    public TenantFilter(TenantResolver resolver, TenantMetricsRecorder metrics, TenantStatusChecker statusChecker) {
        this.resolver = resolver;
        this.metrics = metrics == null ? TenantMetricsRecorder.NOOP : metrics;
        this.statusChecker = statusChecker;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Long tenantId = resolve(request);
        try {
            if (!accept(request, response, tenantId)) {
                return;
            }
            if (tenantId == null) {
                metrics.tenantMissing();
            } else {
                TenantContext.set(tenantId);
                metrics.tenantResolved(tenantId);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean accept(ServletRequest request, ServletResponse response, Long tenantId) throws IOException {
        if (statusChecker == null) {
            return true;
        }
        TenantStatusCheckDecision decision;
        try {
            String path = request instanceof HttpServletRequest httpRequest ? httpRequest.getRequestURI() : null;
            decision = statusChecker.check(path, tenantId);
        } catch (RuntimeException exception) {
            log.error("租户状态校验失败，按拒绝处理：{}", exception.getMessage(), exception);
            decision = TenantStatusCheckDecision.reject(ResultCode.FORBIDDEN, "租户状态校验失败，请稍后重试");
        }
        if (decision.allowed()) {
            return true;
        }
        metrics.tenantRejected(tenantId, decision.errorCode() == null ? 0 : decision.errorCode().code());
        reject(response, decision);
        return false;
    }

    private void reject(ServletResponse response, TenantStatusCheckDecision decision) throws IOException {
        if (!(response instanceof HttpServletResponse httpResponse)) {
            log.warn("响应对象不是 HTTP 响应，无法返回统一报文：{}", decision.message());
            return;
        }
        int code = decision.errorCode() == null ? ResultCode.FORBIDDEN.code() : decision.errorCode().code();
        String message = decision.message() == null ? ResultCode.FORBIDDEN.message() : decision.message();
        httpResponse.setStatus(decision.httpStatus());
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        httpResponse.setContentType("application/json;charset=UTF-8");
        httpResponse.getWriter().write(body(code, message));
        httpResponse.getWriter().flush();
        log.warn("拒绝请求：{}", message);
    }

    private static String body(int code, String message) {
        try {
            return JsonUtils.toJson(Result.fail(code, message));
        } catch (RuntimeException exception) {
            // JSON 库缺失时退化为手写报文，仍返回统一 Result 结构
            return "{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null,\"timestamp\":"
                    + System.currentTimeMillis() + "}";
        }
    }

    private Long resolve(ServletRequest request) {
        if (resolver == null || !(request instanceof HttpServletRequest httpRequest)) {
            return null;
        }
        Long tenantId = resolver.resolve(httpRequest);
        if (tenantId != null) {
            log.debug("请求已解析出租户 [{}]", tenantId);
        }
        return tenantId;
    }
}
