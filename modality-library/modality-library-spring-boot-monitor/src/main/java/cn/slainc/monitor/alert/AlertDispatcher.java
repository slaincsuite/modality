package cn.slainc.monitor.alert;

import cn.slainc.monitor.metrics.MetricsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);

    public static final String NOTIFICATIONS = "modality.alert.notifications";

    private final AlertNotifierSource notifierSource;

    private final MetricsTemplate metrics;

    public AlertDispatcher(AlertNotifierSource notifierSource, MetricsTemplate metrics) {
        if (notifierSource == null) {
            throw new IllegalArgumentException("AlertNotifierSource 不能为空");
        }
        this.notifierSource = notifierSource;
        this.metrics = metrics;
    }

    public int dispatch(AlertEvent event) {
        List<AlertNotifier> notifiers = notifierSource.notifiers();
        int sent = 0;
        for (AlertNotifier notifier : notifiers) {
            if (notifier == null || !notifier.supports(event)) {
                continue;
            }
            try {
                notifier.send(event);
                sent++;
                if (metrics != null) {
                    metrics.increment(NOTIFICATIONS, "notifier", notifier.name(), "result", "success");
                }
            } catch (RuntimeException exception) {
                if (metrics != null) {
                    metrics.increment(NOTIFICATIONS, "notifier", notifier.name(), "result", "failure");
                }
                log.warn("告警通知 {} 发送失败: {}", notifier.name(), exception.getMessage());
            }
        }
        if (sent == 0) {
            log.warn("告警没有任何通道发送成功: {}", event.message());
        }
        return sent;
    }
}
