package cn.slainc.policy.admin;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyStatement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PolicyTemplates {

    public static final String READ_ONLY = "readonly";

    public static final String FULL_ACCESS = "full-access";

    public static final String DENY_DANGEROUS = "deny-dangerous";

    public static final String INTERNAL_ONLY = "internal-only";

    private static final Map<String, PolicyDocument> PRESETS = new LinkedHashMap<>();

    static {
        PRESETS.put(READ_ONLY, of(newStatement("allow-read", Effect.ALLOW,
                List.of("modality:*:get", "modality:*:list", "modality:*:page", "modality:*:query"), List.of("*"), null)));
        PRESETS.put(FULL_ACCESS, of(newStatement("allow-all", Effect.ALLOW, List.of("*"), List.of("*"), null)));
        PRESETS.put(DENY_DANGEROUS, of(newStatement("deny-dangerous", Effect.DENY,
                List.of("modality:*:delete", "modality:*:drop", "modality:*:disable", "modality:*:purge"),
                List.of("*"), null)));
        PRESETS.put(INTERNAL_ONLY, of(newStatement("allow-internal", Effect.ALLOW, List.of("*"), List.of("*"),
                Map.of("ip_in_range", Map.of("modality:SourceIp",
                        List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"))))));
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

    private static PolicyDocument of(PolicyStatement statement) {
        PolicyDocument document = new PolicyDocument();
        document.getStatements().add(statement);
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
