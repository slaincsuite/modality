package cn.slainc.modules.policy.service.generator;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.policy.dto.PolicyCreateRequest;
import cn.slainc.modules.policy.dto.PolicyGeneratorCapabilitiesView;
import cn.slainc.modules.policy.dto.PolicyGeneratorRequest;
import cn.slainc.modules.policy.dto.PolicyGeneratorRule;
import cn.slainc.modules.policy.dto.PolicyGeneratorView;
import cn.slainc.modules.policy.dto.PolicyView;
import cn.slainc.modules.policy.service.catalog.PolicyCatalogService;
import cn.slainc.modules.policy.service.manage.PolicyManageService;
import cn.slainc.policy.domain.ConditionOperator;
import cn.slainc.policy.domain.Effect;
import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyRequest;
import cn.slainc.policy.domain.PolicyStatement;
import cn.slainc.policy.domain.PolicySubjectType;
import cn.slainc.policy.support.PolicyTemplates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PolicyGeneratorService {

    private static final int MAX_RULES = 64;

    private static final int MAX_VALUES = 128;

    private static final int MAX_CONDITION_ENTRIES = 16;

    private static final int MAX_TOTAL_VALUES = 1024;

    private static final int MAX_ACTION_LENGTH = 256;

    private static final int MAX_RESOURCE_LENGTH = 512;

    private static final int MAX_CONDITION_KEY_LENGTH = 128;

    private static final int MAX_CONDITION_VALUE_LENGTH = 512;

    private static final Set<String> BUILTIN_CONDITION_KEYS = Set.of(
            PolicyRequest.KEY_USER_ID,
            PolicyRequest.KEY_TENANT_ID,
            PolicyRequest.KEY_SOURCE_IP,
            PolicyRequest.KEY_CURRENT_TIME,
            PolicyRequest.KEY_LOGIN_TYPE,
            PolicyRequest.KEY_ROLES,
            PolicyRequest.KEY_GROUPS,
            PolicyRequest.KEY_REQUEST_PATH,
            PolicyRequest.KEY_HTTP_METHOD,
            PolicyRequest.KEY_USER_AGENT,
            PolicyRequest.KEY_MFA_VERIFIED,
            PolicyRequest.KEY_SAFE_AUTH);

    private static final List<String> DANGEROUS_ACTIONS = List.of(
            "modality:*:delete",
            "modality:*:purge",
            "modality:*:drop",
            "modality:*:reset-password",
            "modality:*:revoke",
            "modality:*:cleanup",
            "modality:*:clear",
            "modality:*:kick",
            "modality:*:offline",
            "modality:*:transfer",
            "modality:*:import",
            "modality:*:rollback");

    private final PolicyManageService manageService;

    private final PolicyCatalogService catalogService;

    public PolicyGeneratorService(PolicyManageService manageService, PolicyCatalogService catalogService) {
        this.manageService = manageService;
        this.catalogService = catalogService;
    }

    public PolicyGeneratorView preview(PolicyGeneratorRequest request) {
        return generate(request);
    }

    public PolicyView create(PolicyGeneratorRequest request, String operator) {
        PolicyGeneratorView generated = generate(request);
        return manageService.create(new PolicyCreateRequest(request.name(), request.subjectType(),
                request.subjectId(), request.loginType(), request.description(), request.group(),
                generated.document()), operator);
    }

    public PolicyGeneratorCapabilitiesView capabilities() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(PolicyRequest.KEY_USER_ID, "当前用户 ID");
        keys.put(PolicyRequest.KEY_TENANT_ID, "当前租户 ID");
        keys.put(PolicyRequest.KEY_SOURCE_IP, "调用来源 IP，可配合 ip_in_range 限制网段");
        keys.put(PolicyRequest.KEY_CURRENT_TIME, "当前时间，ISO-8601 或毫秒时间戳");
        keys.put(PolicyRequest.KEY_LOGIN_TYPE, "当前账号体系，例如 TENANT_USER、PLATFORM_ADMIN");
        keys.put(PolicyRequest.KEY_ROLES, "当前角色编码列表，可配合 collection_contains 使用");
        keys.put(PolicyRequest.KEY_GROUPS, "当前用户组编码列表，可配合 collection_contains 使用");
        keys.put(PolicyRequest.KEY_REQUEST_PATH, "当前请求路径，可配合 string_like 使用");
        keys.put(PolicyRequest.KEY_HTTP_METHOD, "当前 HTTP 方法");
        keys.put(PolicyRequest.KEY_USER_AGENT, "当前请求 User-Agent");
        keys.put(PolicyRequest.KEY_MFA_VERIFIED, "是否处于二次认证安全窗口");
        keys.put(PolicyRequest.KEY_SAFE_AUTH, "是否处于任意安全验证窗口");
        return new PolicyGeneratorCapabilitiesView(List.of(ConditionOperator.supportedCodes().split(", ")),
                Map.copyOf(keys), List.copyOf(PolicyTemplates.presets().keySet()), DANGEROUS_ACTIONS,
                catalogService.services(),
                Arrays.stream(PolicySubjectType.values()).map(PolicySubjectType::code).toList(),
                Map.of("maxRules", MAX_RULES, "maxValuesPerList", MAX_VALUES,
                        "maxConditionEntries", MAX_CONDITION_ENTRIES, "maxTotalValues", MAX_TOTAL_VALUES));
    }

    private PolicyGeneratorView generate(PolicyGeneratorRequest request) {
        requireRequest(request);
        PolicySubjectType subjectType = PolicySubjectType.of(request.subjectType().trim());
        List<String> warnings = new ArrayList<>();
        List<PolicyStatement> statements = new ArrayList<>();
        Set<String> sids = new LinkedHashSet<>();
        int[] totalValues = {0};
        int index = 0;
        for (PolicyGeneratorRule rule : request.rules()) {
            index++;
            PolicyStatement statement = statement(rule, index, warnings, sids, totalValues);
            statements.add(statement);
        }
        if (request.includeDangerousDenyGuardrail() == null || request.includeDangerousDenyGuardrail()) {
            statements.add(guardrail(sids));
        }
        else {
            warnings.add("危险操作拒绝护栏已关闭，请确认允许主体具备明确的安全治理能力");
        }
        PolicyDocument document = new PolicyDocument();
        document.setStatements(statements);
        document.validate();
        if (statements.stream().noneMatch(item -> item.effectEnum() == Effect.ALLOW)) {
            warnings.add("当前文档没有 allow 语句，策略生效后将只产生拒绝或默认拒绝效果");
        }
        if (request.subjectType() != null && "global".equalsIgnoreCase(request.subjectType().trim())) {
            warnings.add("global 策略会作用于所有租户，请确认资源与条件已限制在预期范围内");
        }
        if (subjectType == PolicySubjectType.ROLE || subjectType == PolicySubjectType.GROUP) {
            warnings.add("role/group 策略是跨租户的全局作用对象，租户内绑定建议使用 tenant-role 或 tenant-group");
        }
        if (statements.stream().anyMatch(item -> item.getResources().contains("*"))) {
            warnings.add("存在 resource 为 * 的语句，建议结合 IP、MFA、租户或时间条件收敛授权范围");
        }
        return new PolicyGeneratorView(document.toJson(), statements.size(),
                (int) statements.stream().filter(item -> item.effectEnum() == Effect.ALLOW).count(),
                (int) statements.stream().filter(item -> item.effectEnum() == Effect.DENY).count(),
                List.copyOf(warnings));
    }

    private PolicyStatement statement(PolicyGeneratorRule rule, int index, List<String> warnings,
                                      Set<String> sids, int[] totalValues) {
        if (rule == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "第 " + index + " 条生成规则不能为空");
        }
        String sid = rule.sid() == null || rule.sid().isBlank() ? "generated-" + index : rule.sid().trim();
        if (sid.length() > 64) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "语句 sid 最多 64 个字符");
        }
        if (!sids.add(sid)) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "生成语句 sid 重复: " + sid);
        }
        Effect effect;
        try {
            effect = Effect.of(rule.effect() == null || rule.effect().isBlank() ? "allow" : rule.effect());
        }
        catch (IllegalArgumentException exception) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "第 " + index + " 条规则: " + exception.getMessage());
        }
        List<String> actions = normalized(rule.actions(), "第 " + index + " 条规则缺少 actions");
        List<String> resources = normalized(rule.resources(), "第 " + index + " 条规则缺少 resources");
        if (actions.size() > MAX_VALUES || resources.size() > MAX_VALUES) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "单条规则 actions/resources 最多 " + MAX_VALUES + " 项");
        }
        String service = normalizedService(rule.service());
        if (service != null && !catalogService.supportsService(service)) {
            warnings.add("服务 " + service + " 不在内置目录中，将按自定义服务生成动作前缀");
        }
        List<String> normalizedActions = actions.stream()
                .map(action -> normalizeAction(action, service)).distinct().toList();
        List<String> normalizedResources = resources.stream()
                .map(resource -> normalizeResource(resource, service)).distinct().toList();
        for (String action : normalizedActions) {
            validateValue(action, MAX_ACTION_LENGTH, "action", false);
            if (service != null && catalogService.supportsService(service)
                    && !catalogService.supportsAction(action)) {
                warnings.add("语句 " + sid + " 的 action " + action + " 不在内置目录中，请确认调用方使用相同动作码");
            }
        }
        for (String resource : normalizedResources) {
            validateValue(resource, MAX_RESOURCE_LENGTH, "resource", false);
        }
        totalValues[0] += normalizedActions.size() + normalizedResources.size();
        if (totalValues[0] > MAX_TOTAL_VALUES) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "策略生成动作与资源总数最多 " + MAX_TOTAL_VALUES + " 项");
        }
        if (normalizedActions.contains("*") || normalizedActions.stream().anyMatch(action -> action.endsWith(":*"))) {
            warnings.add("语句 " + sid + " 使用了范围较大的 action 通配符");
        }
        if (normalizedResources.contains("*")) {
            warnings.add("语句 " + sid + " 对所有资源生效，建议收敛 resource 范围");
        }
        Map<String, Map<String, List<String>>> condition = normalizeCondition(rule.condition(), index, totalValues);
        conditionWarnings(condition, sid, warnings);
        PolicyStatement statement = new PolicyStatement();
        statement.setSid(sid);
        statement.setEffect(effect);
        statement.setActions(normalizedActions);
        statement.setResources(normalizedResources);
        statement.setCondition(condition);
        return statement;
    }

    private PolicyStatement guardrail(Set<String> sids) {
        String sid = "guardrail-dangerous-actions";
        int suffix = 2;
        while (!sids.add(sid)) {
            sid = "guardrail-dangerous-actions-" + suffix++;
        }
        PolicyStatement statement = new PolicyStatement();
        statement.setSid(sid);
        statement.setEffect(Effect.DENY);
        statement.setActions(DANGEROUS_ACTIONS);
        statement.setResources(List.of("*"));
        return statement;
    }

    private void conditionWarnings(Map<String, Map<String, List<String>>> condition, String sid,
                                   List<String> warnings) {
        if (condition == null) {
            return;
        }
        for (Map<String, List<String>> entries : condition.values()) {
            for (String key : entries.keySet()) {
                if (!BUILTIN_CONDITION_KEYS.contains(key)) {
                    warnings.add("语句 " + sid + " 的条件键 " + key + " 不是内置请求属性，需要调用方显式传入 env");
                }
            }
        }
    }

    private void requireRequest(PolicyGeneratorRequest request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_MISSING, "策略生成请求不能为空");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "策略名称不能为空");
        }
        if (request.name().trim().length() > 128) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "策略名称最多 128 个字符");
        }
        if (request.description() != null && request.description().length() > 256) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "策略描述最多 256 个字符");
        }
        if (request.subjectType() == null || request.subjectType().isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "策略作用对象不能为空");
        }
        PolicySubjectType subjectType;
        try {
            subjectType = PolicySubjectType.of(request.subjectType().trim());
        }
        catch (IllegalArgumentException exception) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, exception.getMessage());
        }
        if (subjectType != PolicySubjectType.GLOBAL
                && (request.subjectId() == null || request.subjectId().isBlank())) {
            throw new BizException(ResultCode.PARAM_MISSING, "非 global 策略必须指定作用对象标识");
        }
        if ((subjectType == PolicySubjectType.TENANT_ROLE || subjectType == PolicySubjectType.TENANT_GROUP)
                && !request.subjectId().trim().matches("\\d+:.+")) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "tenant-role/tenant-group 策略的作用对象必须使用 租户ID:角色或用户组编码 格式");
        }
        if (subjectType == PolicySubjectType.USER
                && (request.loginType() == null || request.loginType().isBlank())) {
            throw new BizException(ResultCode.PARAM_MISSING, "user 策略必须指定登录体系");
        }
        if (request.loginType() != null && !request.loginType().isBlank()
                && LoginType.of(request.loginType().trim()) == null) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "不支持的登录体系: " + request.loginType());
        }
        if (request.rules() == null || request.rules().isEmpty()) {
            throw new BizException(ResultCode.PARAM_MISSING, "策略生成规则不能为空");
        }
        if (request.rules().size() > MAX_RULES) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "策略生成规则最多 " + MAX_RULES + " 条");
        }
    }

    private List<String> normalized(List<String> values, String message) {
        if (values == null || values.isEmpty()) {
            throw new BizException(ResultCode.PARAM_MISSING, message);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR, "策略生成值不能包含空项");
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private Map<String, Map<String, List<String>>> normalizeCondition(
            Map<String, Map<String, List<String>>> condition, int ruleIndex, int[] totalValues) {
        if (condition == null || condition.isEmpty()) {
            return null;
        }
        if (condition.size() > MAX_CONDITION_ENTRIES) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    "第 " + ruleIndex + " 条规则条件运算符最多 " + MAX_CONDITION_ENTRIES + " 个");
        }
        Map<String, Map<String, List<String>>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> operatorEntry : condition.entrySet()) {
            if (operatorEntry.getKey() == null || operatorEntry.getKey().isBlank()
                    || operatorEntry.getValue() == null || operatorEntry.getValue().isEmpty()) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR,
                        "第 " + ruleIndex + " 条规则存在空条件运算符");
            }
            try {
                ConditionOperator.of(operatorEntry.getKey().trim());
            }
            catch (IllegalArgumentException exception) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR,
                        "第 " + ruleIndex + " 条规则: " + exception.getMessage());
            }
            Map<String, List<String>> keys = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> keyEntry : operatorEntry.getValue().entrySet()) {
                String key = keyEntry.getKey() == null ? "" : keyEntry.getKey().trim();
                if (key.isEmpty()) {
                    throw new BizException(ResultCode.PARAM_BIND_ERROR,
                            "第 " + ruleIndex + " 条规则存在空条件键");
                }
                if (key.length() > MAX_CONDITION_KEY_LENGTH) {
                    throw new BizException(ResultCode.PARAM_BIND_ERROR,
                            "第 " + ruleIndex + " 条规则条件键最多 " + MAX_CONDITION_KEY_LENGTH + " 个字符");
                }
                List<String> values = normalized(keyEntry.getValue(),
                        "第 " + ruleIndex + " 条规则条件 " + key + " 缺少取值");
                if (values.size() > MAX_VALUES) {
                    throw new BizException(ResultCode.PARAM_BIND_ERROR,
                            "第 " + ruleIndex + " 条规则单个条件最多 " + MAX_VALUES + " 个取值");
                }
                for (String value : values) {
                    validateValue(value, MAX_CONDITION_VALUE_LENGTH, "条件值", true);
                }
                totalValues[0] += values.size();
                if (totalValues[0] > MAX_TOTAL_VALUES) {
                    throw new BizException(ResultCode.PARAM_BIND_ERROR,
                            "策略生成动作、资源与条件取值总数最多 " + MAX_TOTAL_VALUES + " 项");
                }
                keys.put(key, values);
            }
            normalized.put(operatorEntry.getKey().trim(), keys);
        }
        return normalized;
    }

    private void validateValue(String value, int maxLength, String label, boolean allowWhitespace) {
        if (value.length() > maxLength) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    label + " 最多 " + maxLength + " 个字符: " + value);
        }
        for (int index = 0; index < value.length(); index++) {
            if ((!allowWhitespace && Character.isWhitespace(value.charAt(index)))
                    || Character.isISOControl(value.charAt(index))) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR,
                        label + " 不能包含空白或控制字符: " + value);
            }
        }
    }

    private String normalizedService(String service) {
        return service == null || service.isBlank() ? null : service.trim();
    }

    private String normalizeAction(String action, String service) {
        String value = action.trim();
        if (value.startsWith("modality:") || "*".equals(value)) {
            return value;
        }
        if (service == null) {
            return "modality:" + value;
        }
        return value.startsWith(service + ":") ? "modality:" + value : "modality:" + service + ":" + value;
    }

    private String normalizeResource(String resource, String service) {
        String value = resource.trim();
        if (value.startsWith("modality:") || "*".equals(value)) {
            return value;
        }
        return service == null ? value : "modality:" + service + ":" + value;
    }
}
