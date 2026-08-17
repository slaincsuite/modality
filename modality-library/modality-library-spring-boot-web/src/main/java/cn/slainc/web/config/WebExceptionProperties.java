package cn.slainc.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.web.exception")
public class WebExceptionProperties {

    private boolean enabled = true;

    private boolean logStackTrace = true;

    private boolean exposeMessage = true;

    private boolean exposeStackTrace = false;

    private String defaultMessage = "服务器内部错误";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLogStackTrace() {
        return logStackTrace;
    }

    public void setLogStackTrace(boolean logStackTrace) {
        this.logStackTrace = logStackTrace;
    }

    public boolean isExposeMessage() {
        return exposeMessage;
    }

    public void setExposeMessage(boolean exposeMessage) {
        this.exposeMessage = exposeMessage;
    }

    public boolean isExposeStackTrace() {
        return exposeStackTrace;
    }

    public void setExposeStackTrace(boolean exposeStackTrace) {
        this.exposeStackTrace = exposeStackTrace;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public void setDefaultMessage(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }
}
