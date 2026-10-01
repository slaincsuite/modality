package cn.slainc.modules.open.dto;

import java.util.List;

public record AppCreateRequest(String name, String appType, String description, String category, String contact,
                               String logo, List<String> redirectUris, List<String> scopes, List<String> tags) {
}
