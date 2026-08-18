package cn.slainc.web.cors;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

public class CorsFilterConfig {

    public static CorsFilter create(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(properties.getAllowedOriginPatterns());
        configuration.setAllowedMethods(properties.getAllowedMethods());
        configuration.setAllowedHeaders(properties.getAllowedHeaders());
        configuration.setExposedHeaders(properties.getExposedHeaders());
        configuration.setAllowCredentials(properties.isAllowCredentials());
        configuration.setMaxAge(properties.getMaxAge());
        return new CorsFilter(source(configuration, properties));
    }

    private static UrlBasedCorsConfigurationSource source(CorsConfiguration configuration, CorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        for (String pattern : properties.getPathPatterns()) {
            source.registerCorsConfiguration(pattern, configuration);
        }
        return source;
    }
}
