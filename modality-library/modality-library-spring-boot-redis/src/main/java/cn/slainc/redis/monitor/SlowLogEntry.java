package cn.slainc.redis.monitor;

import java.time.Instant;
import java.util.List;

public class SlowLogEntry {

    private final long id;

    private final long timestampSeconds;

    private final long durationMicros;

    private final List<String> command;

    public SlowLogEntry(long id, long timestampSeconds, long durationMicros, List<String> command) {
        this.id = id;
        this.timestampSeconds = timestampSeconds;
        this.durationMicros = durationMicros;
        this.command = command;
    }

    public long getId() {
        return id;
    }

    public long getTimestampSeconds() {
        return timestampSeconds;
    }

    public Instant getTimestamp() {
        return Instant.ofEpochSecond(timestampSeconds);
    }

    public long getDurationMicros() {
        return durationMicros;
    }

    public List<String> getCommand() {
        return command;
    }

    @Override
    public String toString() {
        return "慢查询[编号=" + id + ", 时间=" + getTimestamp() + ", 耗时微秒=" + durationMicros + ", 命令=" + command + "]";
    }
}
