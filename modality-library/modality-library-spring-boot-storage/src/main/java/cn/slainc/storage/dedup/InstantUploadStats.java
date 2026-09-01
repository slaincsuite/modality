package cn.slainc.storage.dedup;

import java.util.concurrent.atomic.AtomicLong;

public class InstantUploadStats {

    private final AtomicLong attempts = new AtomicLong();

    private final AtomicLong hits = new AtomicLong();

    private final AtomicLong savedBytes = new AtomicLong();

    public void recordAttempt() {
        attempts.incrementAndGet();
    }

    public void recordHit(long size) {
        hits.incrementAndGet();
        if (size > 0) {
            savedBytes.addAndGet(size);
        }
    }

    public InstantUploadStat snapshot() {
        long total = attempts.get();
        long hit = hits.get();
        return new InstantUploadStat(total, hit, total == 0 ? 0d : (double) hit / (double) total, savedBytes.get());
    }

    public void reset() {
        attempts.set(0);
        hits.set(0);
        savedBytes.set(0);
    }

    public record InstantUploadStat(long attempts, long hits, double hitRate, long savedBytes) {
    }
}
