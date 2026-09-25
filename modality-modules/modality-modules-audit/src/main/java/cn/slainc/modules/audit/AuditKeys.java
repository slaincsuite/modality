package cn.slainc.modules.audit;


public final class AuditKeys {

    public static final String AUDIT_LOG_TABLE = "t_audit_log";
    public static final String API_LOG_TABLE = "t_audit_api_log";
    public static final String POLICY_LOG_TABLE = "t_audit_policy_log";

    public static final String PERMISSION_LOG_QUERY = "audit:log:query";
    public static final String PERMISSION_LOG_EXPORT = "audit:log:export";
    public static final String PERMISSION_API_LOG_QUERY = "audit:api-log:query";
    public static final String PERMISSION_API_LOG_EXPORT = "audit:api-log:export";
    public static final String PERMISSION_POLICY_LOG_QUERY = "audit:policy-log:query";
    public static final String PERMISSION_ONLINE_QUERY = "audit:online:query";
    public static final String PERMISSION_ONLINE_EXPORT = "audit:online:export";
    public static final String PERMISSION_ONLINE_KICK = "audit:online:kick";
    public static final String PERMISSION_MAINTENANCE = "audit:maintenance:cleanup";

    private AuditKeys() {
    }
}
