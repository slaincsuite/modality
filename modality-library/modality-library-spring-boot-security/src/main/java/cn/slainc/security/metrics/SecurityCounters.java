package cn.slainc.security.metrics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class SecurityCounters implements SecurityMetricsPort {

    private final AtomicLong loginSuccess = new AtomicLong();

    private final AtomicLong loginFailure = new AtomicLong();

    private final AtomicLong authorizationGranted = new AtomicLong();

    private final AtomicLong authorizationDenied = new AtomicLong();

    private final AtomicLong kickOut = new AtomicLong();

    @Override
    public void login(String type, boolean success) {
        (success ? loginSuccess : loginFailure).incrementAndGet();
    }

    @Override
    public void authorization(boolean granted) {
        (granted ? authorizationGranted : authorizationDenied).incrementAndGet();
    }

    @Override
    public void kickOut() {
        kickOut.incrementAndGet();
    }

    public long loginSuccess() {
        return loginSuccess.get();
    }

    public long loginFailure() {
        return loginFailure.get();
    }

    public long authorizationGranted() {
        return authorizationGranted.get();
    }

    public long authorizationDenied() {
        return authorizationDenied.get();
    }

    public long kickOutCount() {
        return kickOut.get();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        snapshot.put("loginSuccess", loginSuccess.get());
        snapshot.put("loginFailure", loginFailure.get());
        snapshot.put("authorizationGranted", authorizationGranted.get());
        snapshot.put("authorizationDenied", authorizationDenied.get());
        snapshot.put("kickOut", kickOutCount());
        return snapshot;
    }
}
