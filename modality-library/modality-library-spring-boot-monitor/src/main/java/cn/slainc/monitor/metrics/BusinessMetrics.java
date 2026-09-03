package cn.slainc.monitor.metrics;

import io.micrometer.core.instrument.Counter;

import java.util.LinkedHashMap;
import java.util.Map;

public class BusinessMetrics {

    public static final String LOGIN = "modality.business.login";

    public static final String REGISTER = "modality.business.register";

    public static final String AUTHORIZATION = "modality.business.authorization";

    public static final String API_CALL = "modality.business.api.call";

    public static final String ERROR = "modality.business.error";

    public static final String ORDER = "modality.business.order";

    public static final String ORDER_AMOUNT = "modality.business.order.amount";

    private static final String UNKNOWN = "未知";

    private final MetricsTemplate metrics;

    private final MetricsSampler sampler;

    public BusinessMetrics(MetricsTemplate metrics, MetricsSampler sampler) {
        if (metrics == null) {
            throw new IllegalArgumentException("MetricsTemplate 不能为空");
        }
        this.metrics = metrics;
        this.sampler = sampler;
        warmUp();
    }

    private void warmUp() {
        metrics.counter(LOGIN);
        metrics.counter(REGISTER);
        metrics.counter(AUTHORIZATION);
        metrics.counter(API_CALL);
        metrics.counter(ERROR);
        metrics.counter(ORDER);
    }

    public void loginSuccess() {
        loginSuccess("default");
    }

    public void loginSuccess(String type) {
        metrics.increment(LOGIN, "result", "success", "type", tag(type));
    }

    public void loginFailure(String reason) {
        loginFailure("default", reason);
    }

    public void loginFailure(String type, String reason) {
        metrics.increment(LOGIN, "result", "failure", "type", tag(type), "reason", tag(reason));
    }

    public void register() {
        register("default");
    }

    public void register(String channel) {
        metrics.increment(REGISTER, "channel", tag(channel));
    }

    public void authorization(boolean granted) {
        metrics.increment(AUTHORIZATION, "result", granted ? "granted" : "denied");
    }

    public void apiCall(boolean success) {
        if (!sampler.shouldRecord()) {
            return;
        }
        metrics.increment(API_CALL, "result", success ? "success" : "failure");
    }

    public void apiCall(String api, boolean success) {
        if (!sampler.shouldRecord()) {
            return;
        }
        metrics.increment(API_CALL, "result", success ? "success" : "failure", "api", tag(api));
    }

    public void error(String type) {
        if (!sampler.shouldRecord()) {
            return;
        }
        metrics.increment(ERROR, "type", tag(type));
    }

    public void order(String status) {
        metrics.increment(ORDER, "status", tag(status));
    }

    public void order(String status, double amount) {
        order(status);
        metrics.record(ORDER_AMOUNT, amount, "status", tag(status));
    }

    public double loginSuccessCount() {
        return sum(LOGIN, "result", "success");
    }

    public double loginFailureCount() {
        return sum(LOGIN, "result", "failure");
    }

    public double registerCount() {
        return sum(REGISTER);
    }

    public double authorizationCount() {
        return sum(AUTHORIZATION);
    }

    public double apiCallCount() {
        return sum(API_CALL);
    }

    public double errorCount() {
        return sum(ERROR);
    }

    public double orderCount() {
        return sum(ORDER);
    }

    public double apiErrorRate() {
        double success = sum(API_CALL, "result", "success");
        double failure = sum(API_CALL, "result", "failure");
        double total = success + failure;
        return total == 0d ? 0d : failure / total;
    }

    public Map<String, Double> snapshot() {
        Map<String, Double> snapshot = new LinkedHashMap<>();
        snapshot.put("loginSuccess", loginSuccessCount());
        snapshot.put("loginFailure", loginFailureCount());
        snapshot.put("register", registerCount());
        snapshot.put("authorization", authorizationCount());
        snapshot.put("apiCall", apiCallCount());
        snapshot.put("apiErrorRate", apiErrorRate());
        snapshot.put("error", errorCount());
        snapshot.put("order", orderCount());
        return snapshot;
    }

    private double sum(String rawName, String... tags) {
        return metrics.registry().find(metrics.name(rawName)).tags(tags).counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private static String tag(String value) {
        return value == null || value.isBlank() ? UNKNOWN : value;
    }
}
