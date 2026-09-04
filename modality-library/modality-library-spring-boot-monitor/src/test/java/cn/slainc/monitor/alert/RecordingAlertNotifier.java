package cn.slainc.monitor.alert;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingAlertNotifier implements AlertNotifier {

    private final String name;

    private final AlertLevel minLevel;

    private final List<AlertEvent> events = new CopyOnWriteArrayList<>();

    public RecordingAlertNotifier() {
        this("recording", AlertLevel.INFO);
    }

    public RecordingAlertNotifier(String name, AlertLevel minLevel) {
        this.name = name;
        this.minLevel = minLevel;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AlertLevel minLevel() {
        return minLevel;
    }

    @Override
    public void send(AlertEvent event) {
        events.add(event);
    }

    public List<AlertEvent> events() {
        return events;
    }

    public AlertEvent single() {
        if (events.size() != 1) {
            throw new IllegalStateException("期望恰好收到 1 条通知，实际 " + events.size() + " 条");
        }
        return events.get(0);
    }

    public void clear() {
        events.clear();
    }
}
