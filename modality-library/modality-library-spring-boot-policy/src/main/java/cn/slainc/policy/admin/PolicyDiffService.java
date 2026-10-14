package cn.slainc.policy.admin;

import cn.slainc.policy.domain.PolicyDocument;
import cn.slainc.policy.domain.PolicyStatement;
import cn.slainc.utils.json.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PolicyDiffService {

    public List<String> diff(PolicyDocument before, PolicyDocument after) {
        List<String> lines = new ArrayList<>();
        Map<String, PolicyStatement> beforeStatements = index(before);
        Map<String, PolicyStatement> afterStatements = index(after);
        for (Map.Entry<String, PolicyStatement> entry : afterStatements.entrySet()) {
            PolicyStatement old = beforeStatements.get(entry.getKey());
            if (old == null) {
                lines.add("新增语句 " + entry.getKey() + ": " + summary(entry.getValue()));
                continue;
            }
            lines.addAll(statementChanges(entry.getKey(), old, entry.getValue()));
        }
        for (Map.Entry<String, PolicyStatement> entry : beforeStatements.entrySet()) {
            if (!afterStatements.containsKey(entry.getKey())) {
                lines.add("删除语句 " + entry.getKey() + ": " + summary(entry.getValue()));
            }
        }
        if (lines.isEmpty()) {
            lines.add("两个策略文档内容一致");
        }
        return lines;
    }

    private Map<String, PolicyStatement> index(PolicyDocument document) {
        Map<String, PolicyStatement> indexed = new LinkedHashMap<>();
        if (document == null || document.getStatements() == null) {
            return indexed;
        }
        int index = 0;
        for (PolicyStatement statement : document.getStatements()) {
            index++;
            String key = statement.getSid() == null || statement.getSid().isBlank()
                    ? "第" + index + "条" : statement.getSid();
            indexed.put(key, statement);
        }
        return indexed;
    }

    private List<String> statementChanges(String key, PolicyStatement before, PolicyStatement after) {
        List<String> changes = new ArrayList<>();
        if (!equalsNullable(before.getEffect(), after.getEffect())) {
            changes.add("语句 " + key + " 的效果由 " + before.getEffect() + " 改为 " + after.getEffect());
        }
        if (!before.getActions().equals(after.getActions())) {
            changes.add("语句 " + key + " 的 action 由 " + before.getActions() + " 改为 " + after.getActions());
        }
        if (!before.getResources().equals(after.getResources())) {
            changes.add("语句 " + key + " 的 resource 由 " + before.getResources() + " 改为 " + after.getResources());
        }
        if (!Objects.equals(before.getCondition(), after.getCondition())) {
            changes.add("语句 " + key + " 的 condition 由 " + conditionText(before.getCondition())
                    + " 改为 " + conditionText(after.getCondition()));
        }
        return changes;
    }

    private String summary(PolicyStatement statement) {
        return statement.getEffect() + " " + statement.getActions() + " → " + statement.getResources();
    }

    private String conditionText(Map<String, Map<String, List<String>>> condition) {
        return condition == null ? "无" : JsonUtils.toJson(condition);
    }

    private boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
