package cn.slainc.web.resource;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "modality.web.static-resource")
public class StaticResourceProperties {

    private boolean enabled = true;

    private Duration cachePeriod = Duration.ofDays(7);

    private boolean cacheControl = true;

    private List<String> patterns = new ArrayList<>(List.of("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg",
            "/**/*.jpeg", "/**/*.gif", "/**/*.svg", "/**/*.ico", "/**/*.woff", "/**/*.woff2", "/**/*.ttf",
            "/**/*.map"));

    private String cdnPrefix;

    private Map<String, String> headers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getCachePeriod() {
        return cachePeriod;
    }

    public void setCachePeriod(Duration cachePeriod) {
        this.cachePeriod = cachePeriod;
    }

    public boolean isCacheControl() {
        return cacheControl;
    }

    public void setCacheControl(boolean cacheControl) {
        this.cacheControl = cacheControl;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<String> patterns) {
        this.patterns = patterns;
    }

    public String getCdnPrefix() {
        return cdnPrefix;
    }

    public void setCdnPrefix(String cdnPrefix) {
        this.cdnPrefix = cdnPrefix;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
}
