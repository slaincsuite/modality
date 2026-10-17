package cn.slainc.policy.support;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.policy.domain.PolicyStatement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PolicyTemplates {

    public static final String READ_ONLY = "readonly";

    public static final String FULL_ACCESS = "full-access";

    public static final String DENY_DANGEROUS = "deny-dangerous";

    public static final String INTERNAL_ONLY = "internal-only";

    public static final String TENANT_ADMIN = "tenant-admin";

    public static final String TENANT_OPERATOR = "tenant-operator";

    public static final String SECURITY_AUDITOR = "security-auditor";

    public static final String POLICY_AUDITOR = "policy-auditor";

    public static final String OPS_OPERATOR = "ops-operator";

    public static final String SUPPORT_AGENT = "support-agent";

    public static final String DEVELOPER = "developer";

    public static final String LIVE_OPERATOR = "live-operator";

    public static final String MFA_SENSITIVE = "mfa-sensitive";

    public static final String OFFICE_ONLY = "office-only";

    public static final String READONLY_INTERNAL = "readonly-internal";

    public static final String MFA_EXPORT_GUARD = "mfa-export-guard";

    public static final String INCIDENT_RESPONDER = "incident-responder";

    public static final String BREAK_GLASS = "break-glass";

    public static final String TENANT_SELF_SERVICE = "tenant-self-service";

    public static final String INTEGRATION_OPERATOR = "integration-operator";

    private static final Map<String, PolicyDocument> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put(READ_ONLY, of(newStatement("allow-read", Effect.ALLOW,
                List.of("modality:*:get", "modality:*:list", "modality:*:page", "modality:*:query"), List.of("*"), null)));
        PRESETS.put(FULL_ACCESS, of(newStatement("allow-all", Effect.ALLOW, List.of("*"), List.of("*"), null)));
        PRESETS.put(DENY_DANGEROUS, of(newStatement("deny-dangerous", Effect.DENY,
                List.of("modality:*:delete", "modality:*:drop", "modality:*:disable", "modality:*:purge"),
                List.of("*"), null)));
        PRESETS.put(INTERNAL_ONLY, of(newStatement("allow-internal", Effect.ALLOW, List.of("*"), List.of("*"),
                Map.of("ip_in_range", Map.of(PolicyRequest.KEY_SOURCE_IP,
                        List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"))))));
        PRESETS.put(TENANT_ADMIN, of(
                newStatement("allow-tenant-identity", Effect.ALLOW,
                        List.of("modality:upms:user:*", "modality:upms:role:*", "modality:upms:group:*",
                                "modality:upms:dept:*"),
                        List.of("modality:upms:user/*", "modality:upms:role/*", "modality:upms:group/*",
                                "modality:upms:dept/*"), null),
                newStatement("allow-tenant-operations", Effect.ALLOW,
                        List.of("modality:workorder:*", "modality:tenant:*:query", "modality:infra:notify:*"),
                        List.of("*"), null),
                newStatement("deny-tenant-high-risk", Effect.DENY,
                        List.of("modality:upms:user:delete", "modality:upms:role:delete",
                                "modality:tenant:manage:delete", "modality:infra:system:alert"),
                        List.of("*"), null)));
        PRESETS.put(TENANT_OPERATOR, of(
                newStatement("allow-tenant-support", Effect.ALLOW,
                        List.of("modality:workorder:*", "modality:upms:user:query", "modality:infra:notify:*"),
                        List.of("*"), null),
                newStatement("deny-identity-write", Effect.DENY,
                        List.of("modality:upms:user:create", "modality:upms:user:update",
                                "modality:upms:user:delete", "modality:upms:role:*"),
                        List.of("*"), null)));
        PRESETS.put(SECURITY_AUDITOR, of(
                newStatement("allow-security-read", Effect.ALLOW,
                        List.of("modality:audit:*:query", "modality:audit:*:export", "modality:upms:user:query",
                                "modality:policy:manage:query", "modality:policy:binding:query"),
                        List.of("*"), null),
                newStatement("deny-security-write", Effect.DENY,
                        List.of("modality:audit:*:update", "modality:audit:*:delete", "modality:upms:*:create",
                                "modality:upms:*:update", "modality:upms:*:delete"),
                        List.of("*"), null)));
        PRESETS.put(POLICY_AUDITOR, of(
                newStatement("allow-policy-read", Effect.ALLOW,
                        List.of("modality:policy:manage:query", "modality:policy:binding:query",
                                "modality:policy:boundary:query", "modality:policy:simulate:run",
                                "modality:policy:test-case:query", "modality:upms:user:query"),
                        List.of("modality:policy/*", "modality:upms:user/*"), null),
                newStatement("deny-policy-mutation", Effect.DENY,
                        List.of("modality:policy:manage:create", "modality:policy:manage:update",
                                "modality:policy:manage:delete", "modality:policy:binding:bind",
                                "modality:policy:binding:unbind", "modality:policy:boundary:update"),
                        List.of("*"), null)));
        PRESETS.put(OPS_OPERATOR, of(
                newStatement("allow-platform-operations", Effect.ALLOW,
                        List.of("modality:infra:*", "modality:job:*", "modality:audit:*:query",
                                "modality:live:*"),
                        List.of("modality:infra/*", "modality:job/*", "modality:live/*"), null),
                newStatement("deny-tenant-administration", Effect.DENY,
                        List.of("modality:tenant:*", "modality:upms:user:*", "modality:upms:role:*"),
                        List.of("*"), null)));
        PRESETS.put(SUPPORT_AGENT, of(
                newStatement("allow-support-workflow", Effect.ALLOW,
                        List.of("modality:workorder:*", "modality:upms:user:query", "modality:infra:notify:*"),
                        List.of("modality:workorder/*", "modality:upms:user/*"), null),
                newStatement("deny-security-administration", Effect.DENY,
                        List.of("modality:audit:*:delete", "modality:upms:role:*", "modality:policy:manage:*",
                                "modality:infra:config:*"),
                        List.of("*"), null)));
        PRESETS.put(DEVELOPER, of(
                newStatement("allow-development", Effect.ALLOW,
                        List.of("modality:open:app:*", "modality:open:scope:*", "modality:infra:file:*",
                                "modality:infra:config:query", "modality:workorder:order:create",
                                "modality:workorder:order:query"),
                        List.of("modality:open/app/*", "modality:infra/file/*"), null),
                newStatement("deny-platform-governance", Effect.DENY,
                        List.of("modality:tenant:*", "modality:upms:role:*", "modality:audit:*:delete",
                                "modality:policy:manage:*", "modality:infra:redis:*", "modality:infra:sql:*"),
                        List.of("*"), null)));
        PRESETS.put(LIVE_OPERATOR, of(
                newStatement("allow-live-operations", Effect.ALLOW,
                        List.of("modality:live:*", "modality:infra:notify:send",
                                "modality:infra:notify:query"),
                        List.of("modality:live/*"), null),
                newStatement("deny-other-administration", Effect.DENY,
                        List.of("modality:upms:*", "modality:tenant:*", "modality:policy:manage:*",
                                "modality:infra:config:*"),
                        List.of("*"), null)));
        PRESETS.put(MFA_SENSITIVE, of(
                newStatement("allow-read", Effect.ALLOW,
                        List.of("modality:*:get", "modality:*:list", "modality:*:page", "modality:*:query"),
                        List.of("*"), null),
                newStatement("allow-write-with-mfa", Effect.ALLOW,
                        List.of("modality:*:create", "modality:*:update", "modality:*:delete",
                                "modality:*:grant", "modality:*:bind"),
                        List.of("*"), Map.of("string_equal", Map.of(PolicyRequest.KEY_MFA_VERIFIED,
                                List.of("true"))))));
        PRESETS.put(OFFICE_ONLY, of(
                newStatement("allow-office-read", Effect.ALLOW,
                        List.of("modality:*:get", "modality:*:list", "modality:*:page", "modality:*:query"),
                        List.of("*"), Map.of("ip_in_range", Map.of(PolicyRequest.KEY_SOURCE_IP,
                                List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"))))));
        PRESETS.put(READONLY_INTERNAL, of(
                newStatement("allow-internal-read", Effect.ALLOW,
                        List.of("modality:*:get", "modality:*:list", "modality:*:page", "modality:*:query",
                                "modality:*:preview", "modality:*:export"),
                        List.of("*"), Map.of("ip_in_range", Map.of(PolicyRequest.KEY_SOURCE_IP,
                                List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"))))));
        PRESETS.put(MFA_EXPORT_GUARD, of(
                newStatement("allow-export-with-mfa", Effect.ALLOW,
                        List.of("modality:*:export"), List.of("*"),
                        Map.of("string_equal", Map.of(PolicyRequest.KEY_MFA_VERIFIED, List.of("true")))),
                newStatement("deny-sensitive-transfer", Effect.DENY,
                        List.of("modality:*:purge", "modality:*:delete", "modality:*:transfer",
                                "modality:*:reset-password"), List.of("*"), null)));
        PRESETS.put(INCIDENT_RESPONDER, of(
                newStatement("allow-incident-investigation", Effect.ALLOW,
                        List.of("modality:audit:*:query", "modality:audit:online:kick",
                                "modality:live:*", "modality:infra:notify:query",
                                "modality:infra:notify:send", "modality:policy:simulate:run"),
                        List.of("modality:audit/*", "modality:live/*", "modality:infra/notify/*",
                                "modality:policy/*"), null),
                newStatement("deny-incident-governance", Effect.DENY,
                        List.of("modality:tenant:*", "modality:upms:role:*", "modality:policy:manage:*",
                                "modality:infra:config:*", "modality:infra:redis:*", "modality:infra:sql:*"),
                        List.of("*"), null)));
        PRESETS.put(BREAK_GLASS, of(
                newStatement("allow-temporary-emergency", Effect.ALLOW, List.of("*"), List.of("*"),
                        Map.of("string_equal", Map.of(PolicyRequest.KEY_MFA_VERIFIED, List.of("true")),
                                "ip_in_range", Map.of(PolicyRequest.KEY_SOURCE_IP,
                                        List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")))),
                newStatement("deny-irreversible-operations", Effect.DENY,
                        List.of("modality:*:purge", "modality:*:drop", "modality:*:transfer",
                                "modality:*:reset-password", "modality:*:clear"), List.of("*"), null)));
        PRESETS.put(TENANT_SELF_SERVICE, of(
                newStatement("allow-tenant-self-service", Effect.ALLOW,
                        List.of("modality:upms:user:query", "modality:upms:role:query",
                                "modality:upms:group:query",
                                "modality:tenant:plan:query", "modality:workorder:*",
                                "modality:infra:notify:query"),
                        List.of("modality:upms/user/*", "modality:upms/role/*", "modality:upms/group/*",
                                "modality:tenant/plan/*", "modality:workorder/*"),
                        Map.of("string_equal", Map.of(PolicyRequest.KEY_LOGIN_TYPE,
                                List.of("TENANT_ADMIN", "TENANT_USER")))),
                newStatement("deny-platform-governance", Effect.DENY,
                        List.of("modality:tenant:manage:*", "modality:upms:system:*",
                                "modality:policy:manage:*", "modality:infra:sql:*",
                                "modality:infra:redis:*"), List.of("*"), null)));
        PRESETS.put(INTEGRATION_OPERATOR, of(
                newStatement("allow-integration-operations", Effect.ALLOW,
                        List.of("modality:open:app:*", "modality:open:scope:*",
                                "modality:open:webhook:*", "modality:infra:apikey:*",
                                "modality:infra:file:*", "modality:workorder:order:query"),
                        List.of("modality:open/*", "modality:infra/apikey/*", "modality:infra/file/*",
                                "modality:workorder/order/*"),
                        Map.of("string_equal", Map.of(PolicyRequest.KEY_LOGIN_TYPE,
                                List.of("PLATFORM_USER", "TENANT_ADMIN", "TENANT_USER")))),
                newStatement("deny-integration-admin", Effect.DENY,
                        List.of("modality:upms:*", "modality:tenant:*", "modality:policy:manage:*",
                                "modality:audit:*:delete"), List.of("*"), null)));
    }

    private PolicyTemplates() {
    }

    public static PolicyDocument template(String name) {
        PolicyDocument document = PRESETS.get(name);
        if (document == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND,
                    "策略模板 " + name + " 不存在，可用模板: " + String.join("、", PRESETS.keySet()));
        }
        return document;
    }

    public static Map<String, PolicyDocument> presets() {
        return Map.copyOf(PRESETS);
    }

    private static PolicyDocument of(PolicyStatement... statements) {
        PolicyDocument document = new PolicyDocument();
        document.getStatements().addAll(List.of(statements));
        document.validate();
        return document;
    }

    private static PolicyStatement newStatement(String sid, Effect effect, List<String> actions,
                                                List<String> resources,
                                                Map<String, Map<String, List<String>>> condition) {
        PolicyStatement statement = new PolicyStatement();
        statement.setSid(sid);
        statement.setEffect(effect);
        statement.setActions(actions);
        statement.setResources(resources);
        statement.setCondition(condition);
        return statement;
    }
}
