package cn.slainc.web.doc;

import cn.slainc.core.result.Result;
import cn.slainc.core.result.ResultCode;
import cn.slainc.utils.json.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class OpenApiDisabledFilter extends OncePerRequestFilter {

    private static final List<String> DOC_PATH_PREFIXES = List.of("/v3/api-docs", "/swagger-ui", "/swagger-ui.html");

    private static final String MESSAGE = "接口文档已关闭";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isDocumentPath(request.getRequestURI())) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(JsonUtils.toJson(Result.fail(ResultCode.NOT_FOUND.code(), MESSAGE)));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isDocumentPath(String uri) {
        return DOC_PATH_PREFIXES.stream().anyMatch(uri::startsWith);
    }
}
