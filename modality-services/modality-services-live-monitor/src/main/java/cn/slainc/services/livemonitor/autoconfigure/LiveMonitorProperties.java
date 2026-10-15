package cn.slainc.services.livemonitor.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 直播监控配置项：业务实现落地后在此扩展
@ConfigurationProperties(prefix = "modality.live-monitor")
public class LiveMonitorProperties {

    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
