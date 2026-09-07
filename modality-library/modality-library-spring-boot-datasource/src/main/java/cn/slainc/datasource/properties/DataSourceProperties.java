package cn.slainc.datasource.properties;

import cn.slainc.datasource.enums.PoolType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.datasource")
public class DataSourceProperties {

    private boolean enabled = true;

    private boolean transactionLogEnabled = false;

    private PoolType poolType = PoolType.HIKARI;

    private final Health health = new Health();

    private final Encrypt encrypt = new Encrypt();

    private final Pool pool = new Pool();

    private final Jta jta = new Jta();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTransactionLogEnabled() {
        return transactionLogEnabled;
    }

    public void setTransactionLogEnabled(boolean transactionLogEnabled) {
        this.transactionLogEnabled = transactionLogEnabled;
    }

    public Health getHealth() {
        return health;
    }

    public Encrypt getEncrypt() {
        return encrypt;
    }

    public Pool getPool() {
        return pool;
    }

    public PoolType getPoolType() {
        return poolType;
    }

    public void setPoolType(PoolType poolType) {
        this.poolType = poolType;
    }

    public Jta getJta() {
        return jta;
    }

    public static class Jta {

        private boolean enabled = false;

        private int transactionTimeout = 60;

        private int minPoolSize = 1;

        private int maxPoolSize = 10;

        private int borrowConnectionTimeout = 30;

        private int maxIdleTime = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTransactionTimeout() {
            return transactionTimeout;
        }

        public void setTransactionTimeout(int transactionTimeout) {
            this.transactionTimeout = transactionTimeout;
        }

        public int getMinPoolSize() {
            return minPoolSize;
        }

        public void setMinPoolSize(int minPoolSize) {
            this.minPoolSize = minPoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getBorrowConnectionTimeout() {
            return borrowConnectionTimeout;
        }

        public void setBorrowConnectionTimeout(int borrowConnectionTimeout) {
            this.borrowConnectionTimeout = borrowConnectionTimeout;
        }

        public int getMaxIdleTime() {
            return maxIdleTime;
        }

        public void setMaxIdleTime(int maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
        }
    }

    public static class Health {

        private boolean enabled = true;

        private long interval = 60_000L;

        private int timeoutSeconds = 3;

        private long slowConnectionThreshold = 1_000L;

        private boolean failoverEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getInterval() {
            return interval;
        }

        public void setInterval(long interval) {
            this.interval = interval;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public long getSlowConnectionThreshold() {
            return slowConnectionThreshold;
        }

        public void setSlowConnectionThreshold(long slowConnectionThreshold) {
            this.slowConnectionThreshold = slowConnectionThreshold;
        }

        public boolean isFailoverEnabled() {
            return failoverEnabled;
        }

        public void setFailoverEnabled(boolean failoverEnabled) {
            this.failoverEnabled = failoverEnabled;
        }
    }

    public static class Encrypt {

        private boolean enabled = false;

        private String key;

        private String keyId = "modality";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
    }

    public static class Pool {

        // Druid 的 maxWait 默认是 -1（无限等待），池不可用时会把调用线程永久挂住，必须给上限
        private Integer maxWaitMillis = 10000;

        private Integer minimumIdle;

        private Integer maximumPoolSize;

        private Long connectionTimeout;

        private Long idleTimeout;

        private Long maxLifetime;

        private Long leakDetectionThreshold;

        public Integer getMaxWaitMillis() {
            return maxWaitMillis;
        }

        public void setMaxWaitMillis(Integer maxWaitMillis) {
            this.maxWaitMillis = maxWaitMillis;
        }

        public Integer getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(Integer minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public Integer getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(Integer maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public Long getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(Long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public Long getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(Long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public Long getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(Long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }

        public Long getLeakDetectionThreshold() {
            return leakDetectionThreshold;
        }

        public void setLeakDetectionThreshold(Long leakDetectionThreshold) {
            this.leakDetectionThreshold = leakDetectionThreshold;
        }
    }
}
