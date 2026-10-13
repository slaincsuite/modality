package cn.slainc.workorder.notify;


public interface WorkOrderNotificationProvider {

    String channel();

    void send(WorkOrderNotification notification);
}
