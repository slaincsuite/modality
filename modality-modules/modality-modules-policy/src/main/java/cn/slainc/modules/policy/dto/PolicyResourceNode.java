package cn.slainc.modules.policy.dto;

import java.util.List;

public record PolicyResourceNode(String code, String name, String type, String resource,
                                 List<PolicyResourceNode> children) {
}
