package cn.slainc.job.autoconfigure;

import cn.slainc.job.support.RetryBackoff;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "modality.job")
public class JobProperties {

    private boolean enabled = true;

    private String keyPrefix = "modality:job";

    private int poolSize = 4;

    private int queueCapacity = 1000;

    private Duration lockTtl = Duration.ZERO;

    private Duration resultTtl = Duration.ofDays(7);

    private Duration defaultRetryInterval = Duration.ofSeconds(5);

    private Retry retry = new Retry();

    private StoreType store = StoreType.AUTO;

    private boolean clustered = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = Math.max(queueCapacity, 1);
    }

    public Duration getLockTtl() {
        return lockTtl;
    }

    public void setLockTtl(Duration lockTtl) {
        this.lockTtl = lockTtl;
    }

    public Duration getResultTtl() {
        return resultTtl;
    }

    public void setResultTtl(Duration resultTtl) {
        this.resultTtl = resultTtl;
    }

    public Duration getDefaultRetryInterval() {
        return defaultRetryInterval;
    }

    public void setDefaultRetryInterval(Duration defaultRetryInterval) {
        this.defaultRetryInterval = defaultRetryInterval;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry == null ? new Retry() : retry;
    }

    public StoreType getStore() {
        return store;
    }

    public void setStore(StoreType store) {
        this.store = store == null ? StoreType.AUTO : store;
    }

    public boolean isClustered() {
        return clustered;
    }

    public void setClustered(boolean clustered) {
        this.clustered = clustered;
    }

    public enum StoreType {

        AUTO,

        JDBC,

        MEMORY
    }

    public static class Retry {

        private Backoff backoff = new Backoff();

        public Backoff getBackoff() {
            return backoff;
        }

        public void setBackoff(Backoff backoff) {
            this.backoff = backoff == null ? new Backoff() : backoff;
        }
    }

    public static class Backoff {

        private double multiplier = RetryBackoff.DEFAULT_MULTIPLIER;

        private Duration maxInterval = RetryBackoff.DEFAULT_MAX_INTERVAL;

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double multiplier) {
            this.multiplier = Math.max(multiplier, 1.0d);
        }

        public Duration getMaxInterval() {
            return maxInterval;
        }

        public void setMaxInterval(Duration maxInterval) {
            this.maxInterval = maxInterval;
        }
    }
}
