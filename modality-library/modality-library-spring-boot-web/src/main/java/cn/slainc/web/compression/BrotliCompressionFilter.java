package cn.slainc.web.compression;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class BrotliCompressionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BrotliCompressionFilter.class);

    private static final String ENCODING = "br";

    private final CompressionProperties properties;

    private final BrotliCodec codec;

    private final List<Pattern> excludedUserAgents;

    public BrotliCompressionFilter(CompressionProperties properties, BrotliCodec codec) {
        this.properties = properties;
        this.codec = codec;
        this.excludedUserAgents = properties.getExcludedUserAgents().stream()
                .filter(StringUtils::hasText)
                .map(Pattern::compile)
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!acceptsCompression(request)) {
            log.debug("跳过 Brotli 压缩：{} {} accept-encoding={}", request.getMethod(), request.getRequestURI(),
                    request.getHeader(HttpHeaders.ACCEPT_ENCODING));
            filterChain.doFilter(request, response);
            return;
        }
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrapper);
        if (request.isAsyncStarted()) {
            return;
        }
        byte[] content = wrapper.getContentAsByteArray();
        if (!compressible(wrapper, content)) {
            log.debug("跳过 Brotli 压缩：{} {} 状态={} 类型={} 长度={}", request.getMethod(), request.getRequestURI(),
                    wrapper.getStatus(), wrapper.getContentType(), content.length);
            wrapper.copyBodyToResponse();
            return;
        }
        byte[] compressed;
        try {
            compressed = codec.compress(content);
        } catch (RuntimeException exception) {
            log.warn("Brotli 压缩失败，本次响应按原文返回：{}", exception.getMessage());
            wrapper.copyBodyToResponse();
            return;
        }
        applyVary(response);
        response.setHeader(HttpHeaders.CONTENT_ENCODING, ENCODING);
        response.setContentLength(compressed.length);
        response.getOutputStream().write(compressed);
        response.flushBuffer();
        log.debug("Brotli 压缩生效：{} {} 由 {} 字节压缩为 {} 字节", request.getMethod(), request.getRequestURI(),
                content.length, compressed.length);
    }

    private boolean acceptsCompression(HttpServletRequest request) {
        if (!properties.isEnabled() || !properties.getBrotli().isEnabled() || !codec.available()) {
            return false;
        }
        if (isWebSocketUpgrade(request)) {
            return false;
        }
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent != null && excludedUserAgents.stream().anyMatch(pattern -> pattern.matcher(userAgent).find())) {
            return false;
        }
        return acceptsBrotli(request.getHeader(HttpHeaders.ACCEPT_ENCODING));
    }

    private boolean isWebSocketUpgrade(HttpServletRequest request) {
        return "websocket".equalsIgnoreCase(request.getHeader(HttpHeaders.UPGRADE));
    }

    static boolean acceptsBrotli(String acceptEncoding) {
        if (!StringUtils.hasText(acceptEncoding)) {
            return false;
        }
        for (String item : acceptEncoding.split(",")) {
            String[] tokens = item.trim().split(";");
            String name = tokens[0].trim();
            if (!name.equalsIgnoreCase(ENCODING) && !"*".equals(name)) {
                continue;
            }
            double quality = 1.0d;
            for (int index = 1; index < tokens.length; index++) {
                String parameter = tokens[index].trim().replace(" ", "").toLowerCase(Locale.ROOT);
                if (parameter.startsWith("q=")) {
                    quality = parseQuality(parameter.substring(2));
                }
            }
            if (quality > 0) {
                return true;
            }
        }
        return false;
    }

    private static double parseQuality(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 1.0d;
        }
    }

    private boolean compressible(ContentCachingResponseWrapper wrapper, byte[] content) {
        int status = wrapper.getStatus();
        if (status < 200 || status >= 300) {
            return false;
        }
        if (wrapper.getHeader(HttpHeaders.CONTENT_ENCODING) != null) {
            return false;
        }
        long minSize = properties.getMinResponseSize() == null ? 0L : properties.getMinResponseSize().toBytes();
        if (content.length == 0 || content.length < minSize) {
            return false;
        }
        String contentType = wrapper.getContentType();
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String mimeType = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        return properties.getMimeTypes().stream()
                .filter(StringUtils::hasText)
                .map(type -> type.trim().toLowerCase(Locale.ROOT))
                .anyMatch(mimeType::equals);
    }

    private void applyVary(HttpServletResponse response) {
        String vary = response.getHeader(HttpHeaders.VARY);
        if (!StringUtils.hasText(vary)) {
            response.setHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
        } else if (!vary.toLowerCase(Locale.ROOT).contains("accept-encoding")) {
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
        }
    }
}
