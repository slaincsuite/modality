package cn.slainc.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.web.response")
public class ResponseWrapProperties {

    private boolean enabled = true;

    private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**", "/error", "/v3/api-docs",
            "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }
}
