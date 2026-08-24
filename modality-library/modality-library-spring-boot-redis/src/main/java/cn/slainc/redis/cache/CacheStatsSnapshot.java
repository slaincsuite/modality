package cn.slainc.redis.cache;

public class CacheStatsSnapshot {

    private final long hits;

    private final long misses;

    private final long loads;

    private final long nullHits;

    private final long degradedLoads;

    public CacheStatsSnapshot(long hits, long misses, long loads, long nullHits, long degradedLoads) {
        this.hits = hits;
        this.misses = misses;
        this.loads = loads;
        this.nullHits = nullHits;
        this.degradedLoads = degradedLoads;
    }

    public long getHits() {
        return hits;
    }

    public long getMisses() {
        return misses;
    }

    public long getLoads() {
        return loads;
    }

    public long getNullHits() {
        return nullHits;
    }

    public long getDegradedLoads() {
        return degradedLoads;
    }

    public double getHitRate() {
        long total = hits + misses;
        return total == 0 ? 0d : (double) hits / total;
    }

    @Override
    public String toString() {
        return "缓存统计[命中=" + hits + ", 未命中=" + misses + ", 加载=" + loads + ", 空值命中=" + nullHits
                + ", 降级加载=" + degradedLoads + ", 命中率=" + String.format("%.4f", getHitRate()) + "]";
    }
}
