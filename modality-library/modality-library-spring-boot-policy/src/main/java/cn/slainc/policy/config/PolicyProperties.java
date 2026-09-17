package cn.slainc.policy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modality.policy")
public class PolicyProperties {

    private boolean enabled = true;

    private String store = "jdbc";

    private long snapshotTtlSeconds = 5;

    private boolean securityBridgeEnabled = true;

    private final DecisionCache decisionCache = new DecisionCache();

    private final Jdbc jdbc = new Jdbc();

    public static class DecisionCache {

        private boolean enabled = true;

        private long ttlSeconds = 60;

        private int maxSize = 10000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }

    public static class Jdbc {

        private String policyTable = "t_policy";

        private String versionTable = "t_policy_version";

        private String auditTable = "t_policy_audit";

        private boolean autoCreateTable = true;

        private int retainedVersions = 10;

        public String getPolicyTable() {
            return policyTable;
        }

        public void setPolicyTable(String policyTable) {
            this.policyTable = policyTable;
        }

        public String getVersionTable() {
            return versionTable;
        }

        public void setVersionTable(String versionTable) {
            this.versionTable = versionTable;
        }

        public String getAuditTable() {
            return auditTable;
        }

        public void setAuditTable(String auditTable) {
            this.auditTable = auditTable;
        }

        public boolean isAutoCreateTable() {
            return autoCreateTable;
        }

        public void setAutoCreateTable(boolean autoCreateTable) {
            this.autoCreateTable = autoCreateTable;
        }

        public int getRetainedVersions() {
            return retainedVersions;
        }

        public void setRetainedVersions(int retainedVersions) {
            this.retainedVersions = retainedVersions;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public long getSnapshotTtlSeconds() {
        return snapshotTtlSeconds;
    }

    public void setSnapshotTtlSeconds(long snapshotTtlSeconds) {
        this.snapshotTtlSeconds = snapshotTtlSeconds;
    }

    public boolean isSecurityBridgeEnabled() {
        return securityBridgeEnabled;
    }

    public void setSecurityBridgeEnabled(boolean securityBridgeEnabled) {
        this.securityBridgeEnabled = securityBridgeEnabled;
    }

    public DecisionCache getDecisionCache() {
        return decisionCache;
    }

    public Jdbc getJdbc() {
        return jdbc;
    }
}
