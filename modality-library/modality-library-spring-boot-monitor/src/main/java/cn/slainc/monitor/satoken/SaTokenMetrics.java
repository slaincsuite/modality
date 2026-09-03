package cn.slainc.monitor.satoken;

import cn.slainc.monitor.metrics.MetricsTemplate;
import io.micrometer.core.instrument.Timer;

import java.util.LinkedHashMap;
import java.util.Map;

public class SaTokenMetrics {

    public static final String LOGIN = "modality.satoken.login";

    public static final String AUTHORIZATION = "modality.satoken.authorization";

    public static final String KICK_OUT = "modality.satoken.kickout";

    private final MetricsTemplate metrics;

    public SaTokenMetrics(MetricsTemplate metrics) {
        if (metrics == null) {
            throw new IllegalArgumentException("MetricsTemplate 不能为空");
        }
        this.metrics = metrics;
        metrics.counter(AUTHORIZATION, "result", "granted");
        metrics.counter(AUTHORIZATION, "result", "denied");
        metrics.counter(KICK_OUT);
    }

    public void login(boolean success) {
        login("default", success);
    }

    public void login(String type, boolean success) {
        metrics.increment(LOGIN, "type", type == null || type.isBlank() ? "default" : type,
                "result", success ? "success" : "failure");
    }

    public Timer.Sample startLogin() {
        return metrics.startSample();
    }

    public long finishLogin(Timer.Sample sample, String type, boolean success) {
        login(type, success);
        return metrics.stopSample(sample, LOGIN, "type", type == null || type.isBlank() ? "default" : type,
                "result", success ? "success" : "failure");
    }

    public void authorization(boolean granted) {
        metrics.increment(AUTHORIZATION, "result", granted ? "granted" : "denied");
    }

    public void kickOut() {
        metrics.increment(KICK_OUT);
    }

    public double loginCount() {
        return metrics.registry().find(metrics.name(LOGIN)).timers().stream().mapToDouble(Timer::count).sum();
    }

    public double authorizationCount() {
        return metrics.counterValue(AUTHORIZATION, "result", "granted")
                + metrics.counterValue(AUTHORIZATION, "result", "denied");
    }

    public double kickOutCount() {
        return metrics.counterValue(KICK_OUT);
    }

    public Map<String, Double> snapshot() {
        Map<String, Double> snapshot = new LinkedHashMap<>();
        snapshot.put("login", loginCount());
        snapshot.put("authorization", authorizationCount());
        snapshot.put("kickOut", kickOutCount());
        return snapshot;
    }
}
