package cn.slainc.webhook.support;

import io.github.resilience4j.core.IntervalFunction;

import java.time.Duration;

public final class WebhookBackoff {

    private static final double MULTIPLIER = 2.0D;

    private WebhookBackoff() {
    }

    public static Duration nextDelay(int attempt, Duration initialBackoff, Duration maxBackoff) {
        long initial = initialBackoff == null ? 0L : Math.max(initialBackoff.toMillis(), 0L);
        long max = maxBackoff == null ? 0L : Math.max(maxBackoff.toMillis(), 0L);
        if (max <= 0L) {
            max = initial;
        }
        if (initial == 0L) {
            return Duration.ZERO;
        }
        long capped = Math.min(initial, max);
        IntervalFunction function = IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(capped), MULTIPLIER, Duration.ofMillis(max));
        long delay = function.apply(Math.max(attempt, 1));
        return Duration.ofMillis(Math.min(Math.max(delay, 0L), max));
    }
}
