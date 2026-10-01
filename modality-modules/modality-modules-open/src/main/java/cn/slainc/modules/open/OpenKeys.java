package cn.slainc.modules.open;

public final class OpenKeys {

    public static final String APP_TABLE = "t_open_app";
    public static final String SCOPE_TABLE = "t_open_scope";
    public static final String CONSENT_TABLE = "t_open_consent";
    public static final String CREDENTIAL_TABLE = "t_open_credential";
    public static final String APP_LOG_TABLE = "t_open_app_log";

    public static final String PERMISSION_APP_QUERY = "open:app:query";
    public static final String PERMISSION_APP_REVIEW = "open:app:review";
    public static final String PERMISSION_APP_OFFLINE = "open:app:offline";
    public static final String PERMISSION_APP_DELETE = "open:app:delete";
    public static final String PERMISSION_SCOPE_QUERY = "open:scope:query";
    public static final String PERMISSION_SCOPE_CREATE = "open:scope:create";
    public static final String PERMISSION_SCOPE_UPDATE = "open:scope:update";
    public static final String PERMISSION_SCOPE_DELETE = "open:scope:delete";
    public static final String PERMISSION_CONSENT_QUERY = "open:consent:query";
    public static final String PERMISSION_CONSENT_EXPORT = "open:consent:export";
    public static final String PERMISSION_CONSENT_REVOKE = "open:consent:revoke";
    public static final String PERMISSION_STATS_QUERY = "open:stats:query";

    public static final String DEFAULT_SCOPE_GROUP = "默认";

    public static final String MASKED_SECRET = "******";

    private OpenKeys() {
    }
}
