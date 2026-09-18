package cn.slainc.openapi.event;

import java.util.List;

public record ConsentGrantedEvent(String userId, String appId, List<String> scopes, long timestamp) {
}
