package cn.slainc.changeaudit.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class ChangeAuditMetrics {

    private final AtomicLong recorded = new AtomicLong();

    private final AtomicLong failed = new AtomicLong();

    public void recordSuccess() {
        recorded.incrementAndGet();
    }

    public void recordFailure() {
        failed.incrementAndGet();
    }

    public long recorded() {
        return recorded.get();
    }

    public long failed() {
        return failed.get();
    }
}
