package cn.slainc.monitor.alert;

import java.util.List;

public interface AlertNotifierSource {

    List<AlertNotifier> notifiers();
}
