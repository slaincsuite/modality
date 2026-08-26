package cn.slainc.tenant.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.tenant.status-check")
public class TenantStatusCheckProperties {

    private boolean enabled = true;

    private List<String> allowedPaths = new ArrayList<>(List.of("/actuator/health", "/actuator/info", "/actuator/health/**",
            "/auth/login", "/auth/logout", "/auth/captcha", "/auth/refresh"));

    private long cacheMaximumSize = 10_000L;

    private Duration cacheExpireAfterWrite = Duration.ofMinutes(1L);

    private MissingTenantPolicy missingTenantPolicy = MissingTenantPolicy.REJECT;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedPaths() {
        return allowedPaths;
    }

    public void setAllowedPaths(List<String> allowedPaths) {
        this.allowedPaths = allowedPaths == null ? new ArrayList<>() : allowedPaths;
    }

    public long getCacheMaximumSize() {
        return cacheMaximumSize;
    }

    public void setCacheMaximumSize(long cacheMaximumSize) {
        this.cacheMaximumSize = cacheMaximumSize;
    }

    public Duration getCacheExpireAfterWrite() {
        return cacheExpireAfterWrite;
    }

    public void setCacheExpireAfterWrite(Duration cacheExpireAfterWrite) {
        this.cacheExpireAfterWrite = cacheExpireAfterWrite;
    }

    public MissingTenantPolicy getMissingTenantPolicy() {
        return missingTenantPolicy;
    }

    public void setMissingTenantPolicy(MissingTenantPolicy missingTenantPolicy) {
        this.missingTenantPolicy = missingTenantPolicy == null ? MissingTenantPolicy.REJECT : missingTenantPolicy;
    }

    public enum MissingTenantPolicy {

        REJECT("拒绝"),
        ALLOW("放行");

        private final String displayName;

        MissingTenantPolicy(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
