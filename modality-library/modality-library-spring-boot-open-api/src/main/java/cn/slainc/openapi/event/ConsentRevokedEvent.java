package cn.slainc.openapi.event;

public record ConsentRevokedEvent(String userId, String appId, long timestamp) {
}
