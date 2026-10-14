package cn.slainc.modules.job.autoconfigure;

import cn.slainc.core.page.PageParam;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "modality.job")
public class JobModuleProperties {

    private boolean enabled = true;

    private boolean autoCreateTable = true;

    private String keyPrefix = "modality:job:console";

    private Duration nodeTtl = Duration.ofMinutes(1);

    private Duration heartbeatInterval = Duration.ofSeconds(20);

    private String nodeId;

    private long maxPageSize = PageParam.MAX_SIZE;

    private boolean logPurgeEnabled = true;

    private Duration logPurgeInterval = Duration.ofHours(1);

    private Duration logRetention = Duration.ofDays(30);

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

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getNodeTtl() {
        return nodeTtl;
    }

    public void setNodeTtl(Duration nodeTtl) {
        this.nodeTtl = nodeTtl;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public long getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(long maxPageSize) {
        this.maxPageSize = maxPageSize < 1L ? PageParam.MAX_SIZE : maxPageSize;
    }

    public boolean isLogPurgeEnabled() {
        return logPurgeEnabled;
    }

    public void setLogPurgeEnabled(boolean logPurgeEnabled) {
        this.logPurgeEnabled = logPurgeEnabled;
    }

    public Duration getLogPurgeInterval() {
        return logPurgeInterval;
    }

    public void setLogPurgeInterval(Duration logPurgeInterval) {
        this.logPurgeInterval = logPurgeInterval;
    }

    public Duration getLogRetention() {
        return logRetention;
    }

    public void setLogRetention(Duration logRetention) {
        this.logRetention = logRetention;
    }
}
