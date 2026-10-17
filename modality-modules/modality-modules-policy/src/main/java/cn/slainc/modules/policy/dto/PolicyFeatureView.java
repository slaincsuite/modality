package cn.slainc.modules.policy.dto;

import java.util.List;

public record PolicyFeatureView(String code, String name, String description, List<PolicyActionView> actions) {
}
