package cn.slainc.redis.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.redis")
public class RedisProperties {

    private final Serializer serializer = new Serializer();

    private final Config config = new Config();

    private final Cache cache = new Cache();

    private final Lock lock = new Lock();

    private final RateLimit rateLimit = new RateLimit();

    private final PubSub pubsub = new PubSub();

    private final Key key = new Key();

    private final Monitor monitor = new Monitor();

    private final Stream stream = new Stream();

    private final Queue queue = new Queue();

    public Serializer getSerializer() {
        return serializer;
    }

    public Config getConfig() {
        return config;
    }

    public Cache getCache() {
        return cache;
    }

    public Lock getLock() {
        return lock;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public PubSub getPubsub() {
        return pubsub;
    }

    public Key getKey() {
        return key;
    }

    public Monitor getMonitor() {
        return monitor;
    }

    public Stream getStream() {
        return stream;
    }

    public Queue getQueue() {
        return queue;
    }

    public static class Serializer {

        private int compressThreshold = 4096;

        private List<String> allowedPackages = new ArrayList<>();

        private List<String> allowedTypes = new ArrayList<>();

        public int getCompressThreshold() {
            return compressThreshold;
        }

        public void setCompressThreshold(int compressThreshold) {
            this.compressThreshold = compressThreshold;
        }

        public List<String> getAllowedPackages() {
            return allowedPackages;
        }

        public void setAllowedPackages(List<String> allowedPackages) {
            this.allowedPackages = allowedPackages == null ? new ArrayList<>() : allowedPackages;
        }

        public List<String> getAllowedTypes() {
            return allowedTypes;
        }

        public void setAllowedTypes(List<String> allowedTypes) {
            this.allowedTypes = allowedTypes == null ? new ArrayList<>() : allowedTypes;
        }
    }

    public static class Config {

        private boolean enabled = true;

        private boolean publishEnabled = true;

        private boolean subscribeEnabled = true;

        private boolean jdbcRepositoryEnabled = true;

        private boolean localCacheEnabled = true;

        private long localCacheMaxSize = 10000;

        private Duration localCacheTtl = Duration.ofMinutes(5);

        private String storeKey = "store";

        private String broadcastChannel = "broadcast";

        private String table = "t_dynamic_config";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPublishEnabled() {
            return publishEnabled;
        }

        public void setPublishEnabled(boolean publishEnabled) {
            this.publishEnabled = publishEnabled;
        }

        public boolean isSubscribeEnabled() {
            return subscribeEnabled;
        }

        public void setSubscribeEnabled(boolean subscribeEnabled) {
            this.subscribeEnabled = subscribeEnabled;
        }

        public boolean isJdbcRepositoryEnabled() {
            return jdbcRepositoryEnabled;
        }

        public void setJdbcRepositoryEnabled(boolean jdbcRepositoryEnabled) {
            this.jdbcRepositoryEnabled = jdbcRepositoryEnabled;
        }

        public boolean isLocalCacheEnabled() {
            return localCacheEnabled;
        }

        public void setLocalCacheEnabled(boolean localCacheEnabled) {
            this.localCacheEnabled = localCacheEnabled;
        }

        public long getLocalCacheMaxSize() {
            return localCacheMaxSize;
        }

        public void setLocalCacheMaxSize(long localCacheMaxSize) {
            this.localCacheMaxSize = localCacheMaxSize;
        }

        public Duration getLocalCacheTtl() {
            return localCacheTtl;
        }

        public void setLocalCacheTtl(Duration localCacheTtl) {
            this.localCacheTtl = localCacheTtl;
        }

        public String getStoreKey() {
            return storeKey;
        }

        public void setStoreKey(String storeKey) {
            this.storeKey = storeKey;
        }

        public String getBroadcastChannel() {
            return broadcastChannel;
        }

        public void setBroadcastChannel(String broadcastChannel) {
            this.broadcastChannel = broadcastChannel;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }
    }

    public static class Cache {

        private boolean enabled = true;

        private boolean springCacheEnabled = true;

        private boolean secondLevelEnabled = true;

        private long secondLevelMaxSize = 10000;

        private Duration secondLevelTtl = Duration.ofMinutes(5);

        private Duration defaultTtl = Duration.ofMinutes(30);

        private Duration nullValueTtl = Duration.ofSeconds(60);

        private double ttlJitter = 0.1d;

        private long lockWaitMillis = 2000L;

        private Duration lockTtl = Duration.ofSeconds(30);

        private long spinIntervalMillis = 50L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSpringCacheEnabled() {
            return springCacheEnabled;
        }

        public void setSpringCacheEnabled(boolean springCacheEnabled) {
            this.springCacheEnabled = springCacheEnabled;
        }

        public boolean isSecondLevelEnabled() {
            return secondLevelEnabled;
        }

        public void setSecondLevelEnabled(boolean secondLevelEnabled) {
            this.secondLevelEnabled = secondLevelEnabled;
        }

        public long getSecondLevelMaxSize() {
            return secondLevelMaxSize;
        }

        public void setSecondLevelMaxSize(long secondLevelMaxSize) {
            this.secondLevelMaxSize = secondLevelMaxSize;
        }

        public Duration getSecondLevelTtl() {
            return secondLevelTtl;
        }

        public void setSecondLevelTtl(Duration secondLevelTtl) {
            this.secondLevelTtl = secondLevelTtl;
        }

        public Duration getDefaultTtl() {
            return defaultTtl;
        }

        public void setDefaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
        }

        public Duration getNullValueTtl() {
            return nullValueTtl;
        }

        public void setNullValueTtl(Duration nullValueTtl) {
            this.nullValueTtl = nullValueTtl;
        }

        public double getTtlJitter() {
            return ttlJitter;
        }

        public void setTtlJitter(double ttlJitter) {
            this.ttlJitter = ttlJitter;
        }

        public long getLockWaitMillis() {
            return lockWaitMillis;
        }

        public void setLockWaitMillis(long lockWaitMillis) {
            this.lockWaitMillis = lockWaitMillis;
        }

        public Duration getLockTtl() {
            return lockTtl;
        }

        public void setLockTtl(Duration lockTtl) {
            this.lockTtl = lockTtl;
        }

        public long getSpinIntervalMillis() {
            return spinIntervalMillis;
        }

        public void setSpinIntervalMillis(long spinIntervalMillis) {
            this.spinIntervalMillis = spinIntervalMillis;
        }
    }

    public static class Lock {

        private boolean enabled = true;

        private long waitTime = 3L;

        private long leaseTime = 30L;

        private boolean failFast = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getWaitTime() {
            return waitTime;
        }

        public void setWaitTime(long waitTime) {
            this.waitTime = waitTime;
        }

        public long getLeaseTime() {
            return leaseTime;
        }

        public void setLeaseTime(long leaseTime) {
            this.leaseTime = leaseTime;
        }

        public boolean isFailFast() {
            return failFast;
        }

        public void setFailFast(boolean failFast) {
            this.failFast = failFast;
        }
    }

    public static class RateLimit {

        private boolean enabled = true;

        private long defaultLimit = 100L;

        private Duration defaultWindow = Duration.ofMinutes(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDefaultLimit() {
            return defaultLimit;
        }

        public void setDefaultLimit(long defaultLimit) {
            this.defaultLimit = defaultLimit;
        }

        public Duration getDefaultWindow() {
            return defaultWindow;
        }

        public void setDefaultWindow(Duration defaultWindow) {
            this.defaultWindow = defaultWindow;
        }
    }

    public static class PubSub {

        private boolean enabled = true;

        private int listenerThreads = 4;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getListenerThreads() {
            return listenerThreads;
        }

        public void setListenerThreads(int listenerThreads) {
            this.listenerThreads = listenerThreads;
        }
    }

    public static class Key {

        private boolean enabled = true;

        private int scanCount = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getScanCount() {
            return scanCount;
        }

        public void setScanCount(int scanCount) {
            this.scanCount = scanCount;
        }
    }

    public static class Monitor {

        private boolean enabled = true;

        private String instance = "default";

        private long slowlogThresholdMicros = 100000L;

        public String getInstance() {
            return instance;
        }

        public void setInstance(String instance) {
            this.instance = instance;
        }

        private int slowlogMaxLength = 128;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSlowlogThresholdMicros() {
            return slowlogThresholdMicros;
        }

        public void setSlowlogThresholdMicros(long slowlogThresholdMicros) {
            this.slowlogThresholdMicros = slowlogThresholdMicros;
        }

        public int getSlowlogMaxLength() {
            return slowlogMaxLength;
        }

        public void setSlowlogMaxLength(int slowlogMaxLength) {
            this.slowlogMaxLength = slowlogMaxLength;
        }
    }

    public static class Stream {

        private boolean enabled = true;

        private int batchSize = 100;

        private int maxRetry = 3;

        private Duration pollTimeout = Duration.ofSeconds(2);

        private String deadLetterSuffix = ":dead";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxRetry() {
            return maxRetry;
        }

        public void setMaxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
        }

        public Duration getPollTimeout() {
            return pollTimeout;
        }

        public void setPollTimeout(Duration pollTimeout) {
            this.pollTimeout = pollTimeout;
        }

        public String getDeadLetterSuffix() {
            return deadLetterSuffix;
        }

        public void setDeadLetterSuffix(String deadLetterSuffix) {
            this.deadLetterSuffix = deadLetterSuffix;
        }
    }

    public static class Queue {

        private boolean enabled = true;

        private long pollInterval = 1000L;

        private int batchSize = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(long pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
