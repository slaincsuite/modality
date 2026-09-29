package cn.slainc.modules.audit.autoconfigure;

import cn.slainc.modules.audit.alert.AuditAlertChannel;
import cn.slainc.modules.audit.alert.MonitorAlertChannel;
import cn.slainc.monitor.alert.AlertNotifier;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;


// monitor 桥：monitor 在类路径时把审计告警转发到平台通知器（钉钉/飞书/企微 webhook 由 monitor 配置）
@AutoConfiguration
@ConditionalOnClass(name = "cn.slainc.monitor.alert.AlertNotifier")
@ConditionalOnProperty(prefix = "modality.audit.alert", name = "monitor-bridge-enabled", matchIfMissing = true)
public class AuditAlertAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MonitorAlertChannel.class)
    public AuditAlertChannel monitorAlertChannel(List<AlertNotifier> notifiers) {
        return new MonitorAlertChannel(notifiers);
    }
}
