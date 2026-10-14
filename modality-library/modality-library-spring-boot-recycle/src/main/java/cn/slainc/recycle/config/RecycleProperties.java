package cn.slainc.recycle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "modality.recycle")
public class RecycleProperties {

    private boolean enabled = true;

    private int retentionDays = 30;

    private int maxSnapshotBytes = 65536;

    private Duration schedulerInterval = Duration.ofHours(1);

    private List<String> entityTypes = new ArrayList<>();

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

    public int getMaxSnapshotBytes() {
        return maxSnapshotBytes;
    }

    public void setMaxSnapshotBytes(int maxSnapshotBytes) {
        this.maxSnapshotBytes = maxSnapshotBytes;
    }

    public Duration getSchedulerInterval() {
        return schedulerInterval;
    }

    public void setSchedulerInterval(Duration schedulerInterval) {
        this.schedulerInterval = schedulerInterval;
    }

    public List<String> getEntityTypes() {
        return entityTypes;
    }

    public void setEntityTypes(List<String> entityTypes) {
        this.entityTypes = entityTypes;
    }
}
