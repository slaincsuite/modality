package cn.slainc.redis.queue;

import java.time.Instant;

public class DelayTask {

    private final String id;

    private final String payload;

    private final long readyAt;

    public DelayTask(String id, String payload, long readyAt) {
        this.id = id;
        this.payload = payload;
        this.readyAt = readyAt;
    }

    public String getId() {
        return id;
    }

    public String getPayload() {
        return payload;
    }

    public long getReadyAt() {
        return readyAt;
    }

    public Instant getReadyInstant() {
        return Instant.ofEpochMilli(readyAt);
    }

    @Override
    public String toString() {
        return "延迟任务[编号=" + id + ", 就绪时间=" + getReadyInstant() + ", 内容=" + payload + "]";
    }
}
