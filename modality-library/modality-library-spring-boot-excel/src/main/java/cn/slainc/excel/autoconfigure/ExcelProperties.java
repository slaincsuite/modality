package cn.slainc.excel.autoconfigure;

import cn.slainc.excel.center.ExportCenter;
import cn.slainc.excel.imports.InMemoryImportLogStore;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.excel")
public class ExcelProperties {

    private boolean enabled = true;

    private final ExportCenterSettings exportCenter = new ExportCenterSettings();

    private final ImportLogSettings importLog = new ImportLogSettings();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ExportCenterSettings getExportCenter() {
        return exportCenter;
    }

    public ImportLogSettings getImportLog() {
        return importLog;
    }

    public static class ExportCenterSettings {

        private boolean enabled = true;

        private String storagePath;

        private int batchSize = ExportCenter.DEFAULT_BATCH_SIZE;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStoragePath() {
            return storagePath;
        }

        public void setStoragePath(String storagePath) {
            this.storagePath = storagePath;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static class ImportLogSettings {

        private boolean enabled = true;

        private int maxEntries = InMemoryImportLogStore.DEFAULT_MAX_ENTRIES;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }
}
