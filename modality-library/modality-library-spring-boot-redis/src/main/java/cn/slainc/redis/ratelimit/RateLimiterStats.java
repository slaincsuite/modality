package cn.slainc.redis.ratelimit;

import java.util.concurrent.atomic.LongAdder;

public class RateLimiterStats {

    private final LongAdder allowed = new LongAdder();

    private final LongAdder rejected = new LongAdder();

    private final LongAdder failed = new LongAdder();

    public void recordAllowed() {
        allowed.increment();
    }

    public void recordRejected() {
        rejected.increment();
    }

    public void recordFailed() {
        failed.increment();
    }

    public long getAllowed() {
        return allowed.sum();
    }

    public long getRejected() {
        return rejected.sum();
    }

    public long getFailed() {
        return failed.sum();
    }

    public double getRejectionRate() {
        long total = allowed.sum() + rejected.sum();
        return total == 0L ? 0d : (double) rejected.sum() / total;
    }

    public void reset() {
        allowed.reset();
        rejected.reset();
        failed.reset();
    }

    @Override
    public String toString() {
        return "限流统计[放行=" + getAllowed() + ", 拒绝=" + getRejected() + ", 异常=" + getFailed()
                + ", 拒绝率=" + String.format("%.4f", getRejectionRate()) + "]";
    }
}
