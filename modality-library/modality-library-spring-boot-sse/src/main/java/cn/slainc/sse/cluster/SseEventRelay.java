package cn.slainc.sse.cluster;

import cn.slainc.sse.support.SseEvent;

public interface SseEventRelay {

    void publish(Long userId, SseEvent event);

    default boolean remote() {
        return false;
    }

    static SseEventRelay local() {
        return (userId, event) -> {
        };
    }
}
