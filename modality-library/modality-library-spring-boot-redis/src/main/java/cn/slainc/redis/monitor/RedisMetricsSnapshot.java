package cn.slainc.redis.monitor;

public class RedisMetricsSnapshot {

    private final long hits;

    private final long misses;

    private final long expiredKeys;

    private final long evictedKeys;

    private final long connectedClients;

    private final long blockedClients;

    private final long usedMemoryBytes;

    private final long totalCommandsProcessed;

    private final long instantaneousOpsPerSec;

    private final long dbSize;

    private final long timestamp;

    public RedisMetricsSnapshot(long hits, long misses, long expiredKeys, long evictedKeys, long connectedClients,
                                long blockedClients, long usedMemoryBytes, long totalCommandsProcessed,
                                long instantaneousOpsPerSec, long dbSize, long timestamp) {
        this.hits = hits;
        this.misses = misses;
        this.expiredKeys = expiredKeys;
        this.evictedKeys = evictedKeys;
        this.connectedClients = connectedClients;
        this.blockedClients = blockedClients;
        this.usedMemoryBytes = usedMemoryBytes;
        this.totalCommandsProcessed = totalCommandsProcessed;
        this.instantaneousOpsPerSec = instantaneousOpsPerSec;
        this.dbSize = dbSize;
        this.timestamp = timestamp;
    }

    public long getHits() {
        return hits;
    }

    public long getMisses() {
        return misses;
    }

    public double getHitRate() {
        long total = hits + misses;
        return total == 0L ? 0d : (double) hits / total;
    }

    public long getExpiredKeys() {
        return expiredKeys;
    }

    public long getEvictedKeys() {
        return evictedKeys;
    }

    public long getConnectedClients() {
        return connectedClients;
    }

    public long getBlockedClients() {
        return blockedClients;
    }

    public long getUsedMemoryBytes() {
        return usedMemoryBytes;
    }

    public long getTotalCommandsProcessed() {
        return totalCommandsProcessed;
    }

    public long getInstantaneousOpsPerSec() {
        return instantaneousOpsPerSec;
    }

    public long getDbSize() {
        return dbSize;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Redis 指标[命中=" + hits + ", 未命中=" + misses + ", 命中率=" + String.format("%.4f", getHitRate())
                + ", 过期键=" + expiredKeys + ", 淘汰键=" + evictedKeys + ", 连接数=" + connectedClients
                + ", 阻塞连接=" + blockedClients + ", 已用内存字节=" + usedMemoryBytes
                + ", 累计命令=" + totalCommandsProcessed + ", 瞬时 QPS=" + instantaneousOpsPerSec
                + ", 键总数=" + dbSize + "]";
    }
}
