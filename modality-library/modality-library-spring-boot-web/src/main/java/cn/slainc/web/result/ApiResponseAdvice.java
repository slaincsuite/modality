package cn.slainc.web.result;

import cn.slainc.core.result.Result;
import cn.slainc.utils.string.RegexUtils;
import cn.slainc.web.config.ResponseWrapProperties;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object>, Ordered {

    private final ResponseWrapProperties properties;

    public ApiResponseAdvice(ResponseWrapProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (returnType.getMethodAnnotation(NoWrap.class) != null) {
            return false;
        }
        if (returnType.getContainingClass().isAnnotationPresent(NoWrap.class)) {
            return false;
        }
        if (Result.class.isAssignableFrom(returnType.getParameterType())) {
            return false;
        }
        if (StringHttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?>) {
            return body;
        }
        String path = request.getURI().getPath();
        if (matchesExclude(path)) {
            return body;
        }
        if (isStreaming(selectedContentType)) {
            return body;
        }
        return Result.ok(body);
    }

    private boolean matchesExclude(String path) {
        List<String> patterns = properties.getExcludePaths();
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String regex = pattern.replace("**", ".*").replace("*", "[^/]*");
            if (RegexUtils.matches(regex, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStreaming(MediaType contentType) {
        if (contentType == null) {
            return false;
        }
        return MediaType.TEXT_EVENT_STREAM.includes(contentType)
                || MediaType.APPLICATION_OCTET_STREAM.includes(contentType)
                || MediaType.APPLICATION_PDF.includes(contentType)
                || MediaType.MULTIPART_FORM_DATA.includes(contentType);
    }
}
