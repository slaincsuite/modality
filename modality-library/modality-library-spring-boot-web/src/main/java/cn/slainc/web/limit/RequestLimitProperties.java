package cn.slainc.web.limit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.web.request-limit")
public class RequestLimitProperties {

    private boolean enabled = false;

    private int maxConcurrentPerIp = 64;

    private int maxConcurrentGlobal = 1024;

    private List<String> excludePaths = new ArrayList<>(
            List.of("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**"));

    private String message = "请求过于频繁，请稍后重试";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxConcurrentPerIp() {
        return maxConcurrentPerIp;
    }

    public void setMaxConcurrentPerIp(int maxConcurrentPerIp) {
        this.maxConcurrentPerIp = maxConcurrentPerIp;
    }

    public int getMaxConcurrentGlobal() {
        return maxConcurrentGlobal;
    }

    public void setMaxConcurrentGlobal(int maxConcurrentGlobal) {
        this.maxConcurrentGlobal = maxConcurrentGlobal;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
