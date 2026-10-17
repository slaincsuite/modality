package cn.slainc.modules.policy.dto;

import java.util.List;
import java.util.Map;

public record PolicyGeneratorCapabilitiesView(List<String> operators, Map<String, String> conditionKeys,
                                              List<String> presets, List<String> guardrailActions,
                                              List<PolicyServiceView> services, List<String> subjectTypes,
                                              Map<String, Integer> limits) {
}
