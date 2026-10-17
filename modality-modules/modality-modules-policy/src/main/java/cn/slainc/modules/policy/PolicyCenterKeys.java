package cn.slainc.modules.policy;

public final class PolicyCenterKeys {

    public static final String POLICY_META_TABLE = "t_policy_meta";

    public static final String POLICY_TEST_CASE_TABLE = "t_policy_test_case";

    public static final String POLICY_TEMPLATE_TABLE = "t_policy_template";

    public static final String POLICY_BOUNDARY_TABLE = "t_policy_boundary";

    public static final String POLICY_BINDING_SOURCE_TABLE = "t_policy_binding_source";

    public static final String DEFAULT_GROUP = "默认";

    public static final String PERMISSION_POLICY_QUERY = "policy:manage:query";
    public static final String PERMISSION_POLICY_CREATE = "policy:manage:create";
    public static final String PERMISSION_POLICY_UPDATE = "policy:manage:update";
    public static final String PERMISSION_POLICY_DELETE = "policy:manage:delete";
    public static final String PERMISSION_POLICY_VERSION = "policy:manage:version";
    public static final String PERMISSION_POLICY_VALIDATE = "policy:manage:validate";
    public static final String PERMISSION_POLICY_GRANT = "policy:manage:grant";
    public static final String PERMISSION_POLICY_TRANSFER = "policy:manage:transfer";
    public static final String PERMISSION_POLICY_CATALOG_QUERY = "policy:catalog:query";
    public static final String PERMISSION_POLICY_TEMPLATE_QUERY = "policy:template:query";
    public static final String PERMISSION_POLICY_TEMPLATE_SAVE = "policy:template:save";
    public static final String PERMISSION_POLICY_BOUNDARY_QUERY = "policy:boundary:query";
    public static final String PERMISSION_POLICY_BOUNDARY_UPDATE = "policy:boundary:update";
    public static final String PERMISSION_SIMULATE_RUN = "policy:simulate:run";
    public static final String PERMISSION_TEST_CASE_QUERY = "policy:test-case:query";
    public static final String PERMISSION_TEST_CASE_SAVE = "policy:test-case:save";
    public static final String PERMISSION_TEST_CASE_DELETE = "policy:test-case:delete";
    public static final String PERMISSION_TEST_CASE_RUN = "policy:test-case:run";
    public static final String PERMISSION_BINDING_QUERY = "policy:binding:query";
    public static final String PERMISSION_BINDING_BIND = "policy:binding:bind";
    public static final String PERMISSION_BINDING_UNBIND = "policy:binding:unbind";
    public static final String PERMISSION_BINDING_EXPORT = "policy:binding:export";

    private PolicyCenterKeys() {
    }
}
