package cn.slainc.sse.support;

import java.time.LocalDateTime;

public record SseEvent(String id, String name, Object data, LocalDateTime time) {
}
