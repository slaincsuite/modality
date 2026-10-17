package cn.slainc.modules.policy.dto;

import java.util.List;

public record PolicyProductView(String code, String name, String description, List<PolicyFeatureView> features) {
}
