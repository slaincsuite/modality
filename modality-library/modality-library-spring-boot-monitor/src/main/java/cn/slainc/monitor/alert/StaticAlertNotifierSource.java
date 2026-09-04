package cn.slainc.monitor.alert;

import java.util.List;

public class StaticAlertNotifierSource implements AlertNotifierSource {

    private final List<AlertNotifier> notifiers;

    public StaticAlertNotifierSource(List<AlertNotifier> notifiers) {
        this.notifiers = notifiers == null ? List.of() : List.copyOf(notifiers);
    }

    @Override
    public List<AlertNotifier> notifiers() {
        return notifiers;
    }
}
