package cn.slainc.web.resource;

import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class StaticResourceConfig implements WebMvcConfigurer {

    private final StaticResourceProperties properties;

    public StaticResourceConfig(StaticResourceProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!properties.isEnabled() || properties.getPatterns().isEmpty()) {
            return;
        }
        ResourceHandlerRegistration registration = registry.addResourceHandler(
                properties.getPatterns().toArray(new String[0]));
        String location = properties.getCdnPrefix();
        if (location != null && !location.isBlank()) {
            String normalized = location.endsWith("/") ? location : location + "/";
            registration.addResourceLocations(normalized);
        } else {
            registration.addResourceLocations("classpath:/static/", "classpath:/public/",
                    "classpath:/META-INF/resources/", "classpath:/resources/");
        }
        registration.setCachePeriod((int) properties.getCachePeriod().toSeconds());
        if (properties.isCacheControl()) {
            registration.setCacheControl(CacheControl.maxAge(properties.getCachePeriod()).cachePublic());
        }
    }
}
