package cn.slainc.monitor.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingAlertNotifier implements AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertNotifier.class);

    public static final String NAME = "log";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void send(AlertEvent event) {
        switch (event.level()) {
            case ERROR -> log.error("{}", event.message());
            case WARN -> log.warn("{}", event.message());
            default -> log.info("{}", event.message());
        }
    }
}
