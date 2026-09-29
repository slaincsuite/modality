package cn.slainc.modules.audit.alert;

import cn.slainc.monitor.alert.AlertEvent;
import cn.slainc.monitor.alert.AlertLevel;
import cn.slainc.monitor.alert.AlertNotifier;
import cn.slainc.monitor.alert.AlertType;
import java.util.List;


// monitor 桥：把审计告警转成平台 AlertEvent 交给 monitor 的通知器（钉钉/飞书/企微 webhook 由 monitor 侧配置）
public class MonitorAlertChannel implements AuditAlertChannel {

    private final List<AlertNotifier> notifiers;

    public MonitorAlertChannel(List<AlertNotifier> notifiers) {
        this.notifiers = notifiers == null ? List.of() : List.copyOf(notifiers);
    }

    @Override
    public String name() {
        return "monitor-bridge";
    }

    @Override
    public void send(AuditAlertMessage message) {
        if (notifiers.isEmpty()) {
            return;
        }
        AlertLevel level = AlertLevel.parse(message.level(), AlertLevel.WARN);
        AlertEvent event = new AlertEvent(message.ruleId(), message.ruleName(), level, AlertType.TRIGGER,
                message.ruleId(), message.unit(), message.value(), message.threshold(), message.timestamp(),
                message.message());
        for (AlertNotifier notifier : notifiers) {
            try {
                notifier.send(event);
            } catch (Exception e) {
                // 渠道失败由上层计数告警，不中断其它通知器
            }
        }
    }
}
