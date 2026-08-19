package cn.slainc.web.timeout;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "modality.web.timeout")
public class RequestTimeoutProperties {

    private boolean enabled = true;

    private Duration asyncTimeout;

    private Duration connectionTimeout = Duration.ofSeconds(20);

    private boolean observe = true;

    private Duration observeTimeout = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getAsyncTimeout() {
        return asyncTimeout;
    }

    public void setAsyncTimeout(Duration asyncTimeout) {
        this.asyncTimeout = asyncTimeout;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public boolean isObserve() {
        return observe;
    }

    public void setObserve(boolean observe) {
        this.observe = observe;
    }

    public Duration getObserveTimeout() {
        return observeTimeout;
    }

    public void setObserveTimeout(Duration observeTimeout) {
        this.observeTimeout = observeTimeout;
    }


}
