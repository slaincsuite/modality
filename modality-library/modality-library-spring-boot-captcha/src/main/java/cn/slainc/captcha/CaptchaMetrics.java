package cn.slainc.captcha;

import java.util.concurrent.atomic.LongAdder;

public class CaptchaMetrics {

    private final LongAdder generated = new LongAdder();

    private final LongAdder verifySuccess = new LongAdder();

    private final LongAdder verifyFailure = new LongAdder();

    private final LongAdder locked = new LongAdder();

    public void recordGenerated() {
        generated.increment();
    }

    public void recordVerifySuccess() {
        verifySuccess.increment();
    }

    public void recordVerifyFailure() {
        verifyFailure.increment();
    }

    public void recordLocked() {
        locked.increment();
    }

    public CaptchaMetricsSnapshot snapshot() {
        return new CaptchaMetricsSnapshot(generated.sum(), verifySuccess.sum(), verifyFailure.sum(), locked.sum());
    }
}
