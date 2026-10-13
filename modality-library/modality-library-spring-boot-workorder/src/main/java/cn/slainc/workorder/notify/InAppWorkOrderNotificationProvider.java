package cn.slainc.workorder.notify;

import cn.slainc.workorder.spi.WorkOrderNotificationStore;


public class InAppWorkOrderNotificationProvider implements WorkOrderNotificationProvider {

    public static final String CHANNEL = "in-app";

    private final WorkOrderNotificationStore store;

    public InAppWorkOrderNotificationProvider(WorkOrderNotificationStore store) {
        this.store = store;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void send(WorkOrderNotification notification) {
        store.save(notification);
    }
}
