package cn.slainc.apikey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.apikey")
public class ApiKeyProperties {

    private boolean enabled = true;

    private String header = "X-API-Key";

    private String namespace = "apikey";

    private String keyPrefix = "sk_";

    private int keyLength = 40;

    private long cacheSeconds = 300;

    private long defaultTtlDays = 90;

    private boolean autoCreateTable = true;

    private Guard guard = new Guard();

    private Stats stats = new Stats();

    public static class Guard {

        private boolean enabled = true;

        private long defaultQpsLimit = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDefaultQpsLimit() {
            return defaultQpsLimit;
        }

        public void setDefaultQpsLimit(long defaultQpsLimit) {
            this.defaultQpsLimit = defaultQpsLimit;
        }
    }

    public static class Stats {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getKeyLength() {
        return keyLength;
    }

    public void setKeyLength(int keyLength) {
        this.keyLength = keyLength;
    }

    public long getCacheSeconds() {
        return cacheSeconds;
    }

    public void setCacheSeconds(long cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }

    public long getDefaultTtlDays() {
        return defaultTtlDays;
    }

    public void setDefaultTtlDays(long defaultTtlDays) {
        this.defaultTtlDays = defaultTtlDays;
    }

    public boolean isAutoCreateTable() {
        return autoCreateTable;
    }

    public void setAutoCreateTable(boolean autoCreateTable) {
        this.autoCreateTable = autoCreateTable;
    }

    public Guard getGuard() {
        return guard;
    }

    public void setGuard(Guard guard) {
        this.guard = guard;
    }

    public Stats getStats() {
        return stats;
    }

    public void setStats(Stats stats) {
        this.stats = stats;
    }
}
