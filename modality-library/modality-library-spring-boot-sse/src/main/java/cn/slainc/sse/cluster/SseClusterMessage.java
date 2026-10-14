package cn.slainc.sse.cluster;

import cn.slainc.sse.support.SseEvent;
import cn.slainc.utils.json.JsonUtils;

public record SseClusterMessage(String origin, Long userId, SseEvent event) {

    public static SseClusterMessage parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JsonUtils.parse(json, SseClusterMessage.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
