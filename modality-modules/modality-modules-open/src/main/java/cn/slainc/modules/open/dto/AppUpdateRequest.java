package cn.slainc.modules.open.dto;

import java.util.List;

public record AppUpdateRequest(String name, String description, String category, String contact, String logo,
                               List<String> redirectUris, List<String> scopes, List<String> tags) {
}
