package cn.slainc.policy.domain;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.utils.json.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PolicyDocument {

    public static final String SUPPORTED_VERSION = "2.0";

    private static final int MAX_JSON_LENGTH = 1_048_576;

    private static final int MAX_STATEMENTS = 128;

    private static final int MAX_VALUES_PER_STATEMENT = 256;

    private static final int MAX_CONDITION_OPERATORS = 32;

    private static final int MAX_CONDITION_KEYS = 64;

    private static final int MAX_CONDITION_VALUES = 256;

    private String version = SUPPORTED_VERSION;

    private List<PolicyStatement> statements = new ArrayList<>();

    public static PolicyDocument of(PolicyStatement... statements) {
        PolicyDocument document = new PolicyDocument();
        for (PolicyStatement statement : statements) {
            document.getStatements().add(statement);
        }
        document.validate();
        return document;
    }

    public static PolicyDocument parse(String json) {
        if (json == null || json.isBlank()) {
            throw new BizException(ResultCode.PARAM_MISSING, "策略文档内容不能为空");
        }
        if (json.length() > MAX_JSON_LENGTH) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "策略文档不能超过 1 MiB");
        }
        PolicyDocument document;
        try {
            document = JsonUtils.parse(json, PolicyDocument.class);
        } catch (RuntimeException exception) {
            throw new BizException(ResultCode.PARAM_TYPE_MISMATCH, "策略文档不是合法的 JSON");
        }
        if (document == null) {
            throw new BizException(ResultCode.PARAM_TYPE_MISMATCH, "策略文档不是合法的 JSON 对象");
        }
        document.validate();
        return document;
    }

    public String toJson() {
        return JsonUtils.toJson(this);
    }

    public void validate() {
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "策略文档版本只支持 " + SUPPORTED_VERSION + "，当前为 " + version);
        }
        if (statements == null || statements.isEmpty()) {
            throw new BizException(ResultCode.PARAM_MISSING, "策略文档至少需要一条语句");
        }
        if (statements.size() > MAX_STATEMENTS) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR, "策略文档最多 " + MAX_STATEMENTS + " 条语句");
        }
        int index = 0;
        for (PolicyStatement statement : statements) {
            index++;
            String prefix = "第 " + index + " 条语句";
            try {
                Effect.of(statement.getEffect());
            } catch (IllegalArgumentException exception) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR, prefix + exception.getMessage());
            }
            if (statement.getActions() == null || statement.getActions().isEmpty()) {
                throw new BizException(ResultCode.PARAM_MISSING, prefix + "缺少 action 列表");
            }
            if (statement.getResources() == null || statement.getResources().isEmpty()) {
                throw new BizException(ResultCode.PARAM_MISSING, prefix + "缺少 resource 列表");
            }
            if (statement.getActions().size() > MAX_VALUES_PER_STATEMENT
                    || statement.getResources().size() > MAX_VALUES_PER_STATEMENT) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR,
                        prefix + "的 action/resource 各最多 " + MAX_VALUES_PER_STATEMENT + " 项");
            }
            if (statement.getActions().stream().anyMatch(action -> action == null || action.isBlank())) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR, prefix + "的 action 列表存在空值");
            }
            if (statement.getResources().stream().anyMatch(resource -> resource == null || resource.isBlank())) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR, prefix + "的 resource 列表存在空值");
            }
            validateCondition(statement.getCondition(), prefix);
        }
    }

    private void validateCondition(Map<String, Map<String, List<String>>> condition, String prefix) {
        if (condition == null) {
            return;
        }
        if (condition.size() > MAX_CONDITION_OPERATORS) {
            throw new BizException(ResultCode.PARAM_BIND_ERROR,
                    prefix + "的条件运算符最多 " + MAX_CONDITION_OPERATORS + " 个");
        }
        for (Map.Entry<String, Map<String, List<String>>> operatorEntry : condition.entrySet()) {
            if (operatorEntry.getKey() == null || operatorEntry.getKey().isBlank()) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR, prefix + "的条件运算符不能为空");
            }
            try {
                ConditionOperator.of(operatorEntry.getKey());
            } catch (IllegalArgumentException exception) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR, prefix + exception.getMessage());
            }
            Map<String, List<String>> keyValues = operatorEntry.getValue();
            if (keyValues == null || keyValues.isEmpty()) {
                throw new BizException(ResultCode.PARAM_MISSING, prefix + "的 " + operatorEntry.getKey() + " 条件缺少条件键");
            }
            if (keyValues.size() > MAX_CONDITION_KEYS) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR,
                        prefix + "的 " + operatorEntry.getKey() + " 条件键最多 " + MAX_CONDITION_KEYS + " 个");
            }
            for (Map.Entry<String, List<String>> keyValue : keyValues.entrySet()) {
                if (keyValue.getKey() == null || keyValue.getKey().isBlank()) {
                    throw new BizException(ResultCode.PARAM_BIND_ERROR,
                            prefix + "的 " + operatorEntry.getKey() + " 条件键不能为空");
                }
                if (keyValue.getValue() == null || keyValue.getValue().isEmpty()) {
                    throw new BizException(ResultCode.PARAM_MISSING,
                            prefix + "的 " + operatorEntry.getKey() + " 条件键 " + keyValue.getKey() + " 缺少取值");
                }
                if (keyValue.getValue().size() > MAX_CONDITION_VALUES) {
                    throw new BizException(ResultCode.PARAM_BIND_ERROR,
                            prefix + "的 " + keyValue.getKey() + " 条件取值最多 " + MAX_CONDITION_VALUES + " 个");
                }
                if (keyValue.getValue().stream().anyMatch(value -> value == null || value.isBlank())) {
                    throw new BizException(ResultCode.PARAM_BIND_ERROR,
                            prefix + "的 " + keyValue.getKey() + " 条件取值不能包含空项");
                }
            }
        }
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<PolicyStatement> getStatements() {
        return statements;
    }

    public void setStatements(List<PolicyStatement> statements) {
        this.statements = statements;
    }
}
