package cn.slainc.modules.policy.dto;

import java.util.List;
import java.util.Map;

public record PolicyGeneratorRule(String sid, String effect, String service, List<String> actions,
                                  List<String> resources,
                                  Map<String, Map<String, List<String>>> condition) {
}
