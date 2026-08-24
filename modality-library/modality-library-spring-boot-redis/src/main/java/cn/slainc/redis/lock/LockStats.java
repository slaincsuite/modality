package cn.slainc.redis.lock;

import java.util.concurrent.atomic.LongAdder;

public class LockStats {

    private final LongAdder acquireSuccess = new LongAdder();

    private final LongAdder acquireFailure = new LongAdder();

    private final LongAdder waited = new LongAdder();

    private final LongAdder totalWaitMillis = new LongAdder();

    private final LongAdder watchdogRenewals = new LongAdder();

    public void recordSuccess(long waitMillis) {
        acquireSuccess.increment();
        if (waitMillis > 0L) {
            waited.increment();
            totalWaitMillis.add(waitMillis);
        }
    }

    public void recordFailure(long waitMillis) {
        acquireFailure.increment();
        if (waitMillis > 0L) {
            waited.increment();
            totalWaitMillis.add(waitMillis);
        }
    }

    public void recordWatchdogRenewal() {
        watchdogRenewals.increment();
    }

    public long getAcquireSuccess() {
        return acquireSuccess.sum();
    }

    public long getAcquireFailure() {
        return acquireFailure.sum();
    }

    public long getWaited() {
        return waited.sum();
    }

    public long getWatchdogRenewals() {
        return watchdogRenewals.sum();
    }

    public long getAverageWaitMillis() {
        long count = waited.sum();
        return count == 0L ? 0L : totalWaitMillis.sum() / count;
    }

    public void reset() {
        acquireSuccess.reset();
        acquireFailure.reset();
        waited.reset();
        totalWaitMillis.reset();
        watchdogRenewals.reset();
    }

    @Override
    public String toString() {
        return "锁统计[成功=" + getAcquireSuccess() + ", 失败=" + getAcquireFailure() + ", 等待次数=" + getWaited()
                + ", 平均等待毫秒=" + getAverageWaitMillis() + ", 看门狗续期=" + getWatchdogRenewals() + "]";
    }
}
