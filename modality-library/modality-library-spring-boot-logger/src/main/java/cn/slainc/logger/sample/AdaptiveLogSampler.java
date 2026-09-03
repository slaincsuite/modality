package cn.slainc.logger.sample;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class AdaptiveLogSampler implements LogSampler {

    private final double targetPerSecond;

    private final double minRate;

    private final long windowMillis;

    private final AtomicLong counter = new AtomicLong();

    private volatile long windowStart = System.currentTimeMillis();

    private volatile double rate = 1.0d;

    public AdaptiveLogSampler(double targetPerSecond, double minRate, long windowMillis) {
        this.targetPerSecond = Math.max(1.0d, targetPerSecond);
        this.minRate = Math.max(0.0d, Math.min(1.0d, minRate));
        this.windowMillis = Math.max(100L, windowMillis);
    }

    @Override
    public String strategy() {
        return "ADAPTIVE";
    }

    @Override
    public double currentRate() {
        return rate;
    }

    public double targetPerSecond() {
        return targetPerSecond;
    }

    @Override
    public boolean accept(String loggerName) {
        long now = System.currentTimeMillis();
        long start = windowStart;
        if (now - start >= windowMillis) {
            synchronized (this) {
                if (now - windowStart >= windowMillis) {
                    long observed = counter.getAndSet(0L);
                    windowStart = now;
                    double observedPerSecond = observed * 1000.0d / windowMillis;
                    if (observedPerSecond > targetPerSecond) {
                        rate = Math.max(minRate, targetPerSecond / observedPerSecond);
                    } else {
                        rate = 1.0d;
                    }
                }
            }
        }
        counter.incrementAndGet();
        double current = rate;
        if (current >= 1.0d) {
            return true;
        }
        if (current <= 0.0d) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < current;
    }
}
