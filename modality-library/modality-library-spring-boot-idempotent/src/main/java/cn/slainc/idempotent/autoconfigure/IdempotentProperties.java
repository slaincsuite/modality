package cn.slainc.idempotent.autoconfigure;

import cn.slainc.core.constant.CacheKeys;
import cn.slainc.idempotent.store.IdempotentStoreType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = IdempotentProperties.PREFIX)
public class IdempotentProperties {

    public static final String PREFIX = "modality.idempotent";

    private boolean enabled = true;

    private Duration defaultTtl = Duration.ofMinutes(5);

    private String message = "请勿重复提交，请稍后再试";

    private List<String> ignoreFields = new ArrayList<>();

    private boolean includeUserId = true;

    private boolean includePath = true;

    private boolean cacheResult = true;

    private boolean failOpen = true;

    private boolean logDuplicate = true;

    private String keyPrefix = CacheKeys.IDEMPOTENT;

    private final Store store = new Store();

    private final Retry retry = new Retry();

    private final Token token = new Token();

    private final Metrics metrics = new Metrics();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getIgnoreFields() {
        return ignoreFields;
    }

    public void setIgnoreFields(List<String> ignoreFields) {
        this.ignoreFields = ignoreFields;
    }

    public boolean isIncludeUserId() {
        return includeUserId;
    }

    public void setIncludeUserId(boolean includeUserId) {
        this.includeUserId = includeUserId;
    }

    public boolean isIncludePath() {
        return includePath;
    }

    public void setIncludePath(boolean includePath) {
        this.includePath = includePath;
    }

    public boolean isCacheResult() {
        return cacheResult;
    }

    public void setCacheResult(boolean cacheResult) {
        this.cacheResult = cacheResult;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public boolean isLogDuplicate() {
        return logDuplicate;
    }

    public void setLogDuplicate(boolean logDuplicate) {
        this.logDuplicate = logDuplicate;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Store getStore() {
        return store;
    }

    public Retry getRetry() {
        return retry;
    }

    public Token getToken() {
        return token;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public static class Store {

        private IdempotentStoreType type = IdempotentStoreType.REDIS;

        private String tableName = "t_idempotent_record";

        private boolean autoCreateTable = true;

        public IdempotentStoreType getType() {
            return type;
        }

        public void setType(IdempotentStoreType type) {
            this.type = type;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public boolean isAutoCreateTable() {
            return autoCreateTable;
        }

        public void setAutoCreateTable(boolean autoCreateTable) {
            this.autoCreateTable = autoCreateTable;
        }
    }

    public static class Retry {

        private int maxRetries = 3;

        private Duration window = Duration.ofMinutes(5);

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public static class Token {

        private boolean enabled = true;

        private Duration defaultTtl = Duration.ofMinutes(5);

        private String headerName = "X-Idempotent-Token";

        private boolean bindUser = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getDefaultTtl() {
            return defaultTtl;
        }

        public void setDefaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public boolean isBindUser() {
            return bindUser;
        }

        public void setBindUser(boolean bindUser) {
            this.bindUser = bindUser;
        }
    }

    public static class Metrics {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
