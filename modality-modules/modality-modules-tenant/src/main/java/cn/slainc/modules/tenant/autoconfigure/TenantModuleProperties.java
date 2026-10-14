package cn.slainc.modules.tenant.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.tenant-module")
public class TenantModuleProperties {

    private boolean enabled = true;

    private boolean autoCreateTable = true;

    private final PlanDefaults plan = new PlanDefaults();

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

    public PlanDefaults getPlan() {
        return plan;
    }

    // 套餐未配置维度的静态全局默认；动态配置 modality.tenant-module.plan.* 优先于这些值
    public static class PlanDefaults {

        private long maxFileSizeMb = 100L;

        private int logRetentionDays = 90;

        private int rateLimitPerMinute = 100;

        private int maxConcurrentExports = 5;

        private int searchMaxPageSize = 100;

        public long getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(long maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }

        public int getLogRetentionDays() {
            return logRetentionDays;
        }

        public void setLogRetentionDays(int logRetentionDays) {
            this.logRetentionDays = logRetentionDays;
        }

        public int getRateLimitPerMinute() {
            return rateLimitPerMinute;
        }

        public void setRateLimitPerMinute(int rateLimitPerMinute) {
            this.rateLimitPerMinute = rateLimitPerMinute;
        }

        public int getMaxConcurrentExports() {
            return maxConcurrentExports;
        }

        public void setMaxConcurrentExports(int maxConcurrentExports) {
            this.maxConcurrentExports = maxConcurrentExports;
        }

        public int getSearchMaxPageSize() {
            return searchMaxPageSize;
        }

        public void setSearchMaxPageSize(int searchMaxPageSize) {
            this.searchMaxPageSize = searchMaxPageSize;
        }
    }
}
