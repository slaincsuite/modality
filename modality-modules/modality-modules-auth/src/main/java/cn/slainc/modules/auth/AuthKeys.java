package cn.slainc.modules.auth;

import java.util.List;

public final class AuthKeys {

    public static final String ACCOUNT_CLOSURE_TABLE = "t_auth_account_closure";

    public static final String MFA_FACTOR_TABLE = "t_mfa_factor";

    public static final String SECURITY_POLICY_TABLE = "t_auth_security_policy";

    public static final String FEISHU_APP_CONFIG_TABLE = "t_auth_feishu_app_config";

    public static final String THIRD_SYNC_RECORD_TABLE = "t_auth_third_sync_record";

    public static final String PASSKEY_TABLE = "t_auth_passkey";

    public static final String PERMISSION_USER_QUERY = "upms:user:query";

    public static final String PERMISSION_USER_UPDATE = "upms:user:update";

    public static final String PERMISSION_MFA_POLICY_QUERY = "auth:mfa-policy:query";

    public static final String PERMISSION_MFA_POLICY_UPDATE = "auth:mfa-policy:update";

    public static final String PERMISSION_LOGIN_POLICY_QUERY = "auth:login-policy:query";

    public static final String PERMISSION_LOGIN_POLICY_UPDATE = "auth:login-policy:update";

    public static final String PERMISSION_SESSION_POLICY_QUERY = "auth:session-policy:query";

    public static final String PERMISSION_SESSION_POLICY_UPDATE = "auth:session-policy:update";

    public static final String PERMISSION_THIRD_CONFIG_QUERY = "auth:third-config:query";

    public static final String PERMISSION_THIRD_CONFIG_UPDATE = "auth:third-config:update";

    public static final String PERMISSION_THIRD_SYNC_EXECUTE = "auth:third-sync:execute";

    public static final String PERMISSION_THIRD_SYNC_QUERY = "auth:third-sync:query";

    public static final String PERMISSION_THIRD_SYNC_EXPORT = "auth:third-sync:export";

    public static final String DYNAMIC_ACTIVE_TIMEOUT_KEY = "modality.security.session.active-timeout";

    public static final List<String> DYNAMIC_SESSION_TIMEOUT_KEYS = List.of(
            "modality.security.session.timeout-platform-admin",
            "modality.security.session.timeout-platform-user",
            "modality.security.session.timeout-tenant-admin",
            "modality.security.session.timeout-tenant-user",
            "modality.security.session.timeout-app",
            "modality.security.session.timeout-service",
            "modality.security.session.timeout-guest"
    );

    private AuthKeys() {
    }
}
