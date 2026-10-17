package cn.slainc.modules.open.dto;

import java.util.List;

public record ConsentView(String id, String userId, String appId, String appName, List<String> scopes,
                          long grantedAt, Long revokedAt, String source, String revokeReason) {
}
