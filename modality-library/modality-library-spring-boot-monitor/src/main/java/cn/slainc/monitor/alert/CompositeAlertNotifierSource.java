package cn.slainc.monitor.alert;

import java.util.ArrayList;
import java.util.List;

public class CompositeAlertNotifierSource implements AlertNotifierSource {

    private final List<AlertNotifierSource> sources;

    public CompositeAlertNotifierSource(List<AlertNotifierSource> sources) {
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    @Override
    public List<AlertNotifier> notifiers() {
        List<AlertNotifier> notifiers = new ArrayList<>();
        for (AlertNotifierSource source : sources) {
            if (source != null) {
                notifiers.addAll(source.notifiers());
            }
        }
        return notifiers;
    }
}
