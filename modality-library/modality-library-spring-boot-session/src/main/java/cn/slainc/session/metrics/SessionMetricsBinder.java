package cn.slainc.session.metrics;

import cn.slainc.monitor.metrics.MetricsTemplate;
import cn.slainc.session.stats.SessionStats;
import cn.slainc.session.stats.SessionStatsListener;
import cn.slainc.session.stats.SessionStatsService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class SessionMetricsBinder implements SessionStatsListener {

    public static final String ONLINE = "modality.session.online";

    public static final String AVG_DURATION = "modality.session.avg-duration-seconds";

    public static final String ALL_TYPE = "ALL";

    private final SessionStatsService statsService;

    private final Map<String, AtomicLong> online = new LinkedHashMap<>();

    private final AtomicLong avgDuration = new AtomicLong();

    public SessionMetricsBinder(MetricsTemplate metrics, SessionStatsService statsService) {
        this.statsService = statsService;
        for (String type : List.of("ADMIN", "USER", "CLIENT", ALL_TYPE)) {
            AtomicLong value = new AtomicLong();
            online.put(type, value);
            metrics.gauge(ONLINE, value, AtomicLong::get, "type", type);
        }
        metrics.gauge(AVG_DURATION, avgDuration, AtomicLong::get);
    }

    // 构造期不能读统计：通知回调会按类型解析 SessionStatsListener，撞上正在创建的本 Bean
    public void refresh() {
        statsService.snapshot(null);
    }

    @Override
    public void onSnapshot(SessionStats stats) {
        if (stats == null) {
            return;
        }
        long total = 0;
        for (String type : List.of("ADMIN", "USER", "CLIENT")) {
            online.computeIfAbsent(type, key -> new AtomicLong()).set(0L);
        }
        for (Map.Entry<String, Long> entry : stats.byType().entrySet()) {
            AtomicLong value = online.get(entry.getKey());
            if (value != null) {
                value.set(entry.getValue());
            }
            total += entry.getValue();
        }
        online.get(ALL_TYPE).set(total);
        avgDuration.set((long) stats.avgDurationSeconds());
    }

    public double online(String type) {
        AtomicLong value = online.get(type == null ? ALL_TYPE : type);
        return value == null ? 0d : value.get();
    }

    public double avgDurationSeconds() {
        return avgDuration.get();
    }

    public Map<String, Double> snapshot() {
        Map<String, Double> result = new LinkedHashMap<>();
        online.forEach((key, value) -> result.put(key, (double) value.get()));
        result.put("avgDurationSeconds", (double) avgDuration.get());
        return result;
    }
}
