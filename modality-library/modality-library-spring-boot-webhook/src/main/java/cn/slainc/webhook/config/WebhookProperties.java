package cn.slainc.webhook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "modality.webhook")
public class WebhookProperties {

    private boolean enabled = true;

    private int maxRetry = 5;

    private long timeoutMillis = 5000L;

    private Duration initialBackoff = Duration.ofSeconds(30);

    private Duration maxBackoff = Duration.ofMinutes(30);

    private int batchSize = 100;

    private long signatureToleranceSeconds = 300L;

    private int threadPoolSize = 4;

    private Duration retryInterval = Duration.ofSeconds(60);

    private String signatureHeader = "X-Modality-Signature";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getSignatureToleranceSeconds() {
        return signatureToleranceSeconds;
    }

    public void setSignatureToleranceSeconds(long signatureToleranceSeconds) {
        this.signatureToleranceSeconds = signatureToleranceSeconds;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public Duration getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(Duration retryInterval) {
        this.retryInterval = retryInterval;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }
}
