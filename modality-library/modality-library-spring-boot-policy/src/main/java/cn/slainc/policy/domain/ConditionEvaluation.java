package cn.slainc.policy.domain;

import java.util.List;

public record ConditionEvaluation(String operator, String key, Object actual, List<String> expected, boolean passed) {
}
