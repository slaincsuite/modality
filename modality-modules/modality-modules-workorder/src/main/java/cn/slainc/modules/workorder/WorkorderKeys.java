package cn.slainc.modules.workorder;


public final class WorkorderKeys {

    public static final String ORDER_TABLE = "t_work_order";

    public static final String REPLY_TABLE = "t_work_order_reply";

    public static final String ACTIVITY_TABLE = "t_work_order_activity";

    public static final String ATTACHMENT_TABLE = "t_work_order_attachment";

    public static final String COMMENT_TABLE = "t_work_order_comment";

    public static final String SUBSCRIPTION_TABLE = "t_work_order_subscription";

    public static final String NOTIFICATION_TABLE = "t_work_order_notification";

    public static final String PERMISSION_ORDER_QUERY = "workorder:order:query";

    public static final String PERMISSION_ORDER_ASSIGN = "workorder:order:assign";

    public static final String PERMISSION_ORDER_CLOSE = "workorder:order:close";

    public static final String PERMISSION_ORDER_EXPORT = "workorder:order:export";

    public static final String PERMISSION_STATS_QUERY = "workorder:stats:query";

    public static final String PERMISSION_COMMENT_MANAGE = "workorder:comment:manage";

    public static final String PERMISSION_ATTACHMENT_MANAGE = "workorder:attachment:manage";

    public static final String PERMISSION_NOTIFICATION_MANAGE = "workorder:notification:manage";

    private WorkorderKeys() {
    }
}
