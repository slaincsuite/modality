package cn.slainc.modules.policy.dto;

import java.util.List;

public record PolicyGeneratorView(String document, int statementCount, int allowCount, int denyCount,
                                  List<String> warnings) {
}
