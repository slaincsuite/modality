package cn.slainc.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.web.trace")
public class TraceProperties {

    private boolean enabled = true;

    private String headerName = "X-Trace-Id";

    private boolean acceptUpstream = true;

    private boolean uuidStyle = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public boolean isAcceptUpstream() {
        return acceptUpstream;
    }

    public void setAcceptUpstream(boolean acceptUpstream) {
        this.acceptUpstream = acceptUpstream;
    }

    public boolean isUuidStyle() {
        return uuidStyle;
    }

    public void setUuidStyle(boolean uuidStyle) {
        this.uuidStyle = uuidStyle;
    }
}
