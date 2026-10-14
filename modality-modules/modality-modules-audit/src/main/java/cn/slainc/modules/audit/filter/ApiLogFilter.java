package cn.slainc.modules.audit.filter;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import cn.slainc.core.constant.HttpHeaders;
import cn.slainc.core.context.RequestContext;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.audit.config.AuditConfigReader;
import cn.slainc.modules.audit.entity.ApiLogRecord;
import cn.slainc.modules.audit.writer.ApiLogWriter;
import cn.slainc.security.StpKit;
import cn.slainc.utils.net.IpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


// API 访问日志过滤器：错误请求与慢请求必记，正常请求按采样率记录；记录动作本身走异步写入器
public class ApiLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiLogFilter.class);

    private static final String DEFAULT_LOGIN_TYPE_HEADER = "X-Login-Type";

    private final ApiLogWriter writer;

    private final AuditConfigReader configReader;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final String[] excludePatterns;

    public ApiLogFilter(ApiLogWriter writer, AuditConfigReader configReader, String excludePaths) {
        this.writer = writer;
        this.configReader = configReader;
        this.excludePatterns = excludePaths == null || excludePaths.isBlank()
                ? new String[0]
                : excludePaths.split(",");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : excludePatterns) {
            if (pathMatcher.match(pattern.trim(), path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            record(request, response, System.currentTimeMillis() - start);
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response, long costMillis) {
        try {
            int status = response.getStatus();
            boolean error = status >= 400;
            long slowThreshold = configReader.apiSlowThresholdMillis();
            boolean slow = costMillis >= slowThreshold;
            if (!error && !slow && !sampled()) {
                return;
            }
            ApiLogRecord record = new ApiLogRecord();
            record.setTraceId(RequestContext.traceId());
            resolveLogin(request, record);
            record.setTenantId(TenantContext.get());
            record.setClientIp(clientIp(request));
            record.setUserAgent(truncate(request.getHeader("User-Agent"), 256));
            record.setHttpMethod(request.getMethod());
            record.setRequestPath(truncate(request.getRequestURI(), 256));
            record.setStatusCode(status);
            record.setCostMillis(costMillis);
            record.setSlow(slow);
            record.setError(error);
            record.setCreateTime(System.currentTimeMillis());
            writer.submit(record);
        } catch (Exception e) {
            // 访问日志绝不影响业务请求
            log.debug("API 访问日志记录失败：{}", e.getMessage());
        }
    }

    private boolean sampled() {
        double rate = configReader.apiSamplingRate();
        if (rate <= 0) {
            return false;
        }
        return rate >= 1 || ThreadLocalRandom.current().nextDouble() < rate;
    }

    private void resolveLogin(HttpServletRequest request, ApiLogRecord record) {
        String headerValue = request.getHeader(DEFAULT_LOGIN_TYPE_HEADER);
        LoginType type = headerValue == null ? LoginType.TENANT_USER : LoginType.of(headerValue);
        record.setLoginType(type == null ? headerValue : type.value());
        if (type != null) {
            try {
                Object loginId = StpKit.of(type).getLoginIdDefaultNull();
                if (loginId != null) {
                    record.setLoginId(String.valueOf(loginId));
                }
            } catch (Exception e) {
                log.debug("解析在线账号失败：{}", e.getMessage());
            }
        }
    }

    private String clientIp(HttpServletRequest request) {
        return IpUtils.resolveClientIp(request.getHeader(HttpHeaders.FORWARDED_FOR),
                request.getHeader(HttpHeaders.REAL_IP),
                request.getHeader(HttpHeaders.CF_CONNECTING_IP),
                request.getRemoteAddr());
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
