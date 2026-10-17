package cn.slainc.modules.upms.dto;

import java.util.Map;

public record UserPreferencesView(String scene, Map<String, Object> settings, Long updateTime) {
}
