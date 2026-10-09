package cn.slainc.modules.infra.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "modality.infra")
public class InfraModuleProperties {

    private boolean enabled = true;

    private boolean autoCreateTable = true;

    private String configHistoryKey;

    private Duration dictCacheTtl = Duration.ofMinutes(5);

    private long logDownloadMaxBytes = 32L * 1024 * 1024;

    private final File file = new File();

    private final Notify notify = new Notify();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoCreateTable() {
        return autoCreateTable;
    }

    public void setAutoCreateTable(boolean autoCreateTable) {
        this.autoCreateTable = autoCreateTable;
    }

    public String getConfigHistoryKey() {
        return configHistoryKey;
    }

    public void setConfigHistoryKey(String configHistoryKey) {
        this.configHistoryKey = configHistoryKey;
    }

    public Duration getDictCacheTtl() {
        return dictCacheTtl;
    }

    public void setDictCacheTtl(Duration dictCacheTtl) {
        this.dictCacheTtl = dictCacheTtl;
    }

    public long getLogDownloadMaxBytes() {
        return logDownloadMaxBytes;
    }

    public void setLogDownloadMaxBytes(long logDownloadMaxBytes) {
        this.logDownloadMaxBytes = logDownloadMaxBytes;
    }

    public File getFile() {
        return file;
    }

    public Notify getNotify() {
        return notify;
    }

    public static class File {

        private int previewTextMaxBytes = 65536;

        private int chunkContextMax = 100;

        private boolean chunkSessionRedis = true;

        private Duration chunkSessionTtl = Duration.ofHours(24);

        public boolean isChunkSessionRedis() {
            return chunkSessionRedis;
        }

        public void setChunkSessionRedis(boolean chunkSessionRedis) {
            this.chunkSessionRedis = chunkSessionRedis;
        }

        public Duration getChunkSessionTtl() {
            return chunkSessionTtl;
        }

        public void setChunkSessionTtl(Duration chunkSessionTtl) {
            this.chunkSessionTtl = chunkSessionTtl;
        }

        public int getPreviewTextMaxBytes() {
            return previewTextMaxBytes;
        }

        public void setPreviewTextMaxBytes(int previewTextMaxBytes) {
            this.previewTextMaxBytes = previewTextMaxBytes;
        }

        public int getChunkContextMax() {
            return chunkContextMax;
        }

        public void setChunkContextMax(int chunkContextMax) {
            this.chunkContextMax = chunkContextMax;
        }
    }

    public static class Notify {

        private int connectTimeoutMillis = 3000;

        private int readTimeoutMillis = 5000;

        private int maxRetry = 3;

        private boolean allowInternalWebhook = false;

        public boolean isAllowInternalWebhook() {
            return allowInternalWebhook;
        }

        public void setAllowInternalWebhook(boolean allowInternalWebhook) {
            this.allowInternalWebhook = allowInternalWebhook;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public void setReadTimeoutMillis(int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
        }

        public int getMaxRetry() {
            return maxRetry;
        }

        public void setMaxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
        }
    }
}
