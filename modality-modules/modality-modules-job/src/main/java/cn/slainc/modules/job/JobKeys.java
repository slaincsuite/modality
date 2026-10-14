package cn.slainc.modules.job;

// 定时任务中心表名与管理端点权限码：控制器一律引用常量，不写字面量
public final class JobKeys {

    public static final String TASK_TABLE = "t_job_task";

    public static final String LOG_TABLE = "t_job_log";

    public static final String PERMISSION_TASK_QUERY = "job:task:query";

    public static final String PERMISSION_TASK_CREATE = "job:task:create";

    public static final String PERMISSION_TASK_UPDATE = "job:task:update";

    public static final String PERMISSION_TASK_DELETE = "job:task:delete";

    public static final String PERMISSION_TASK_TRIGGER = "job:task:trigger";

    public static final String PERMISSION_TASK_PREVIEW = "job:task:preview";

    public static final String PERMISSION_LOG_QUERY = "job:log:query";

    public static final String PERMISSION_LOG_DELETE = "job:log:delete";

    public static final String PERMISSION_LOG_CLEAN = "job:log:clean";

    public static final String PERMISSION_NODE_QUERY = "job:node:query";

    private JobKeys() {
    }
}
