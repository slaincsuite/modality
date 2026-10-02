package cn.slainc.modules.infra.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.infra")
public class InfraModuleProperties {

    private boolean enabled = true;

    private boolean autoCreateTable = true;

    private String configHistoryKey;

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

    public File getFile() {
        return file;
    }

    public Notify getNotify() {
        return notify;
    }

    public static class File {

        private int previewTextMaxBytes = 65536;

        private int chunkContextMax = 100;

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
