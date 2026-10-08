package cn.slainc.web.cors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

public class CorsFilterConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsFilterConfig.class);

    public static CorsFilter create(CorsProperties properties) {
        List<String> patterns = usablePatterns(properties.getAllowedOriginPatterns());
        if (patterns.isEmpty()) {
            log.warn("未配置允许的跨域来源，跨域请求不会获得 CORS 授权头");
            return new CorsFilter(new UrlBasedCorsConfigurationSource());
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(patterns);
        configuration.setAllowedMethods(properties.getAllowedMethods());
        configuration.setAllowedHeaders(properties.getAllowedHeaders());
        configuration.setExposedHeaders(properties.getExposedHeaders());
        configuration.setAllowCredentials(resolveAllowCredentials(patterns, properties.isAllowCredentials()));
        configuration.setMaxAge(properties.getMaxAge());
        return new CorsFilter(source(configuration, properties));
    }

    private static List<String> usablePatterns(List<String> patterns) {
        if (patterns == null) {
            return List.of();
        }
        return patterns.stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .map(String::trim)
                .toList();
    }

    private static boolean resolveAllowCredentials(List<String> patterns, boolean allowCredentials) {
        if (!allowCredentials) {
            return false;
        }
        boolean wildcard = patterns == null || patterns.isEmpty()
                || patterns.stream().anyMatch(pattern -> pattern != null && pattern.trim().equals("*"));
        if (wildcard) {
            log.error("跨域配置为通配源时禁止携带凭证，已强制关闭 allowCredentials");
            return false;
        }
        return true;
    }

    private static UrlBasedCorsConfigurationSource source(CorsConfiguration configuration, CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        for (String pattern : properties.getPathPatterns()) {
            source.registerCorsConfiguration(pattern, configuration);
        }
        return source;
    }
}
