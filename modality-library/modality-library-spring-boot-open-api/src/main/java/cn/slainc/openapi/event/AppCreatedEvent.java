package cn.slainc.openapi.event;

public record AppCreatedEvent(String appId, String name, String ownerId, long timestamp) {
}
