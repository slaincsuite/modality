package cn.slainc.job.support;

import io.github.resilience4j.core.IntervalFunction;

import java.time.Duration;

public final class RetryBackoff {

    public static final double DEFAULT_MULTIPLIER = 2.0d;

    public static final Duration DEFAULT_MAX_INTERVAL = Duration.ofMinutes(5);

    private static final long MIN_INTERVAL_MILLIS = 1L;

    private RetryBackoff() {
    }

    public static IntervalFunction of(Duration initialInterval, double multiplier, Duration maxInterval) {
        long initial = millisAtLeastOne(initialInterval);
        long max = maxInterval == null ? DEFAULT_MAX_INTERVAL.toMillis() : millisAtLeastOne(maxInterval);
        return IntervalFunction.ofExponentialBackoff(initial, Math.max(multiplier, 1.0d), max);
    }

    private static long millisAtLeastOne(Duration duration) {
        if (duration == null) {
            return MIN_INTERVAL_MILLIS;
        }
        return Math.max(duration.toMillis(), MIN_INTERVAL_MILLIS);
    }
}
