package cn.slainc.modules.open.store;

import java.util.List;

public record ConsentQueryRow(String id, String userId, String appId, String appName, List<String> scopes,
                              long grantedAt, Long revokedAt, String source, String revokeReason) {
}
