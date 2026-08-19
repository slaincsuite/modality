package cn.slainc.web.body;

import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.utils.json.JsonUtils;
import cn.slainc.utils.string.RegexUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RequestBodyLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestBodyLimitFilter.class);

    private final RequestBodyProperties properties;

    public RequestBodyLimitFilter(RequestBodyProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (properties.isEnabled() && !matchesExclude(request.getRequestURI()) && exceedsLimit(request)) {
            log.warn("请求体超过限制已拒绝 [{} {}] contentLength={} 限制={} 字节", request.getMethod(),
                    request.getRequestURI(), request.getContentLengthLong(), properties.getMaxRequestSize().toBytes());
            writeRejection(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean exceedsLimit(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        return contentLength > properties.getMaxRequestSize().toBytes();
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

    private void writeRejection(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body = Result.fail(ResultCode.PAYLOAD_TOO_LARGE.code(), properties.getMessage());
        response.getWriter().write(JsonUtils.toJson(body));
    }
}
