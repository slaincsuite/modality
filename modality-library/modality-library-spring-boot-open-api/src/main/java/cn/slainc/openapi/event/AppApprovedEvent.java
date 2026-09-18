package cn.slainc.openapi.event;

public record AppApprovedEvent(String appId, String name, String ownerId, long timestamp) {
}
