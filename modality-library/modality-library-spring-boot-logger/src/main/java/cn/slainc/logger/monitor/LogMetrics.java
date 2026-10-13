package cn.slainc.logger.monitor;

public interface LogMetrics {

    void record(String level, String loggerName, boolean hasException);

    LogMetricsSnapshot snapshot();

    default LogMetricsSnapshot windowSnapshot(long windowMillis) {
        return snapshot();
    }

    default void reset() {
    }
}
