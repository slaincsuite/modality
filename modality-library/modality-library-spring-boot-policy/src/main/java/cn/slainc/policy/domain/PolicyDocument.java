package cn.slainc.policy.domain;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.result.ResultCode;
import cn.slainc.utils.json.JsonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PolicyDocument {

    public static final String SUPPORTED_VERSION = "2.0";

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
        PolicyDocument document = JsonUtils.parse(json, PolicyDocument.class);
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
        for (Map.Entry<String, Map<String, List<String>>> operatorEntry : condition.entrySet()) {
            try {
                ConditionOperator.of(operatorEntry.getKey());
            } catch (IllegalArgumentException exception) {
                throw new BizException(ResultCode.PARAM_BIND_ERROR, prefix + exception.getMessage());
            }
            Map<String, List<String>> keyValues = operatorEntry.getValue();
            if (keyValues == null || keyValues.isEmpty()) {
                throw new BizException(ResultCode.PARAM_MISSING, prefix + "的 " + operatorEntry.getKey() + " 条件缺少条件键");
            }
            for (Map.Entry<String, List<String>> keyValue : keyValues.entrySet()) {
                if (keyValue.getValue() == null || keyValue.getValue().isEmpty()) {
                    throw new BizException(ResultCode.PARAM_MISSING,
                            prefix + "的 " + operatorEntry.getKey() + " 条件键 " + keyValue.getKey() + " 缺少取值");
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
