package cn.slainc.logger.monitor;

public interface LogAlertHook {

    void onAlert(LogAlert alert);

    default int order() {
        return 0;
    }
}
