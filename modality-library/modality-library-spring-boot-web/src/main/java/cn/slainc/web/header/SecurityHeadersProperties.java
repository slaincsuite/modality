package cn.slainc.web.header;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.web.security-headers")
public class SecurityHeadersProperties {

    private boolean enabled = true;

    private String contentTypeOptions = "nosniff";

    private String frameOptions = "SAMEORIGIN";

    private boolean hstsEnabled = false;

    private long hstsMaxAge = 31536000L;

    private boolean hstsIncludeSubDomains = true;

    private String contentSecurityPolicy;

    private String referrerPolicy = "strict-origin-when-cross-origin";

    private String permissionsPolicy = "geolocation=(), microphone=(), camera=()";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getContentTypeOptions() {
        return contentTypeOptions;
    }

    public void setContentTypeOptions(String contentTypeOptions) {
        this.contentTypeOptions = contentTypeOptions;
    }

    public String getFrameOptions() {
        return frameOptions;
    }

    public void setFrameOptions(String frameOptions) {
        this.frameOptions = frameOptions;
    }

    public boolean isHstsEnabled() {
        return hstsEnabled;
    }

    public void setHstsEnabled(boolean hstsEnabled) {
        this.hstsEnabled = hstsEnabled;
    }

    public long getHstsMaxAge() {
        return hstsMaxAge;
    }

    public void setHstsMaxAge(long hstsMaxAge) {
        this.hstsMaxAge = hstsMaxAge;
    }

    public boolean isHstsIncludeSubDomains() {
        return hstsIncludeSubDomains;
    }

    public void setHstsIncludeSubDomains(boolean hstsIncludeSubDomains) {
        this.hstsIncludeSubDomains = hstsIncludeSubDomains;
    }

    public String getContentSecurityPolicy() {
        return contentSecurityPolicy;
    }

    public void setContentSecurityPolicy(String contentSecurityPolicy) {
        this.contentSecurityPolicy = contentSecurityPolicy;
    }

    public String getReferrerPolicy() {
        return referrerPolicy;
    }

    public void setReferrerPolicy(String referrerPolicy) {
        this.referrerPolicy = referrerPolicy;
    }

    public String getPermissionsPolicy() {
        return permissionsPolicy;
    }

    public void setPermissionsPolicy(String permissionsPolicy) {
        this.permissionsPolicy = permissionsPolicy;
    }
}
