package cn.slainc.modules.audit.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = "modality.audit")
public class AuditProperties {

    private boolean enabled = true;

    private boolean autoCreateTable = true;

    private final ApiLog apiLog = new ApiLog();

    private final PolicyLog policyLog = new PolicyLog();

    private final Cleanup cleanup = new Cleanup();

    private final Alert alert = new Alert();

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

    public ApiLog getApiLog() {
        return apiLog;
    }

    public PolicyLog getPolicyLog() {
        return policyLog;
    }

    public Cleanup getCleanup() {
        return cleanup;
    }

    public Alert getAlert() {
        return alert;
    }

    public static class ApiLog {

        private boolean enabled = true;

        private double samplingRate = 1.0;

        private long slowThresholdMillis = 3000;

        private int queueCapacity = 10000;

        private int filterOrder = -6500;

        private String excludePaths = "/actuator/**,/favicon.ico,/error";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getSamplingRate() {
            return samplingRate;
        }

        public void setSamplingRate(double samplingRate) {
            this.samplingRate = samplingRate;
        }

        public long getSlowThresholdMillis() {
            return slowThresholdMillis;
        }

        public void setSlowThresholdMillis(long slowThresholdMillis) {
            this.slowThresholdMillis = slowThresholdMillis;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getFilterOrder() {
            return filterOrder;
        }

        public void setFilterOrder(int filterOrder) {
            this.filterOrder = filterOrder;
        }

        public String getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(String excludePaths) {
            this.excludePaths = excludePaths;
        }
    }

    public static class PolicyLog {

        private boolean enabled = true;

        private boolean recordAllow = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRecordAllow() {
            return recordAllow;
        }

        public void setRecordAllow(boolean recordAllow) {
            this.recordAllow = recordAllow;
        }
    }

    public static class Cleanup {

        private boolean enabled = true;

        private int retentionDays = 90;

        private long intervalSeconds = 86400;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public long getIntervalSeconds() {
            return intervalSeconds;
        }

        public void setIntervalSeconds(long intervalSeconds) {
            this.intervalSeconds = intervalSeconds;
        }
    }

    public static class Alert {

        private boolean enabled = true;

        private long evaluateIntervalSeconds = 300;

        private long windowMinutes = 10;

        private int riskyOperationThreshold = 20;

        private int loginFailureThreshold = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getEvaluateIntervalSeconds() {
            return evaluateIntervalSeconds;
        }

        public void setEvaluateIntervalSeconds(long evaluateIntervalSeconds) {
            this.evaluateIntervalSeconds = evaluateIntervalSeconds;
        }

        public long getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(long windowMinutes) {
            this.windowMinutes = windowMinutes;
        }

        public int getRiskyOperationThreshold() {
            return riskyOperationThreshold;
        }

        public void setRiskyOperationThreshold(int riskyOperationThreshold) {
            this.riskyOperationThreshold = riskyOperationThreshold;
        }

        public int getLoginFailureThreshold() {
            return loginFailureThreshold;
        }

        public void setLoginFailureThreshold(int loginFailureThreshold) {
            this.loginFailureThreshold = loginFailureThreshold;
        }
    }
}
