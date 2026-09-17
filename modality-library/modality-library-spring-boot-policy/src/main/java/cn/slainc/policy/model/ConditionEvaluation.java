package cn.slainc.policy.model;

import java.util.List;

public record ConditionEvaluation(String operator, String key, Object actual, List<String> expected, boolean passed) {
}
