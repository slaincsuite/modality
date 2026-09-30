package cn.slainc.modules.policy;

public final class PolicyCenterKeys {

    public static final String POLICY_META_TABLE = "t_policy_meta";

    public static final String POLICY_TEST_CASE_TABLE = "t_policy_test_case";

    public static final String DEFAULT_GROUP = "默认";

    public static final String PERMISSION_POLICY_QUERY = "policy:manage:query";
    public static final String PERMISSION_POLICY_CREATE = "policy:manage:create";
    public static final String PERMISSION_POLICY_UPDATE = "policy:manage:update";
    public static final String PERMISSION_POLICY_DELETE = "policy:manage:delete";
    public static final String PERMISSION_POLICY_VERSION = "policy:manage:version";
    public static final String PERMISSION_POLICY_TRANSFER = "policy:manage:transfer";
    public static final String PERMISSION_SIMULATE_RUN = "policy:simulate:run";
    public static final String PERMISSION_TEST_CASE_QUERY = "policy:test-case:query";
    public static final String PERMISSION_TEST_CASE_SAVE = "policy:test-case:save";
    public static final String PERMISSION_TEST_CASE_DELETE = "policy:test-case:delete";
    public static final String PERMISSION_TEST_CASE_RUN = "policy:test-case:run";
    public static final String PERMISSION_BINDING_QUERY = "policy:binding:query";
    public static final String PERMISSION_BINDING_BIND = "policy:binding:bind";
    public static final String PERMISSION_BINDING_UNBIND = "policy:binding:unbind";

    private PolicyCenterKeys() {
    }
}
