package cn.slainc.monitor.alert;

public interface AlertNotifier {

    String name();

    void send(AlertEvent event);

    default AlertLevel minLevel() {
        return AlertLevel.INFO;
    }

    default boolean supports(AlertEvent event) {
        return event != null && event.level().ordinal() >= minLevel().ordinal();
    }
}
