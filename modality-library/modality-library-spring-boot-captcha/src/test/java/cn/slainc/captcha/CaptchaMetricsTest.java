package cn.slainc.captcha;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptchaMetricsTest {

    @Test
    void snapshotStartsAtZero() {
        CaptchaMetricsSnapshot snapshot = new CaptchaMetrics().snapshot();
        assertEquals(0L, snapshot.generated());
        assertEquals(0L, snapshot.verifySuccess());
        assertEquals(0L, snapshot.verifyFailure());
        assertEquals(0L, snapshot.locked());
    }

    @Test
    void countersAccumulate() {
        CaptchaMetrics metrics = new CaptchaMetrics();
        metrics.recordGenerated();
        metrics.recordGenerated();
        metrics.recordVerifySuccess();
        metrics.recordVerifyFailure();
        metrics.recordVerifyFailure();
        metrics.recordVerifyFailure();
        metrics.recordLocked();
        CaptchaMetricsSnapshot snapshot = metrics.snapshot();
        assertEquals(2L, snapshot.generated());
        assertEquals(1L, snapshot.verifySuccess());
        assertEquals(3L, snapshot.verifyFailure());
        assertEquals(1L, snapshot.locked());
    }

    @Test
    void snapshotIsPointInTime() {
        CaptchaMetrics metrics = new CaptchaMetrics();
        metrics.recordGenerated();
        CaptchaMetricsSnapshot before = metrics.snapshot();
        metrics.recordGenerated();
        assertEquals(1L, before.generated());
        assertEquals(2L, metrics.snapshot().generated());
    }
}
