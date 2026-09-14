package cn.slainc.session.stats;

import cn.slainc.core.enums.LoginType;
import cn.slainc.session.SessionInfo;
import cn.slainc.session.device.DeviceTypeStats;
import cn.slainc.session.device.DeviceTypeNormalizer;
import cn.slainc.session.query.SessionScanOutcome;
import cn.slainc.session.query.SessionScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SessionStatsService {

    private static final Logger log = LoggerFactory.getLogger(SessionStatsService.class);

    private final SessionScanner scanner;

    private final ObjectProvider<SessionStatsListener> listeners;

    public SessionStatsService(SessionScanner scanner, ObjectProvider<SessionStatsListener> listeners) {
        this.scanner = scanner;
        this.listeners = listeners;
    }

    public SessionStats snapshot(LoginType type) {
        List<SessionScanOutcome> outcomes = scanner.scanAll(type);
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> byDevice = new LinkedHashMap<>();
        Map<String, Long> byTenant = new LinkedHashMap<>();
        long total = 0;
        long durationSeconds = 0;
        boolean truncated = false;
        String message = null;
        LocalDateTime now = LocalDateTime.now();
        for (SessionScanOutcome outcome : outcomes) {
            if (outcome.truncated()) {
                truncated = true;
                if (message == null) {
                    message = outcome.message();
                }
            }
            for (SessionInfo session : outcome.sessions()) {
                total++;
                byType.merge(session.loginType(), 1L, Long::sum);
                byDevice.merge(session.deviceCategory(), 1L, Long::sum);
                byTenant.merge(session.tenantKey(), 1L, Long::sum);
                if (session.loginTime() != null) {
                    long seconds = Duration.between(session.loginTime(), now).getSeconds();
                    durationSeconds += Math.max(seconds, 0);
                }
            }
        }
        double average = total == 0 ? 0d : (double) durationSeconds / total;
        SessionStats stats = new SessionStats(total, byType, byDevice, byTenant, average, truncated, message);
        notifyListeners(stats);
        return stats;
    }

    public long onlineCount(LoginType type) {
        return snapshot(type).total();
    }

    public Map<String, Long> countByDeviceType() {
        return snapshot(null).byDeviceType();
    }

    public Map<String, Long> countByTenant() {
        return snapshot(null).byTenant();
    }

    public Map<String, Long> countByType() {
        return snapshot(null).byType();
    }

    public DeviceTypeStats deviceTypeStats(LoginType type) {
        SessionStats stats = snapshot(type);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String category : List.of(DeviceTypeNormalizer.PC, DeviceTypeNormalizer.MOBILE,
                DeviceTypeNormalizer.TABLET, DeviceTypeNormalizer.UNKNOWN)) {
            counts.put(category, stats.byDeviceType().getOrDefault(category, 0L));
        }
        return new DeviceTypeStats(counts, stats.total(), stats.truncated(), stats.message());
    }

    private void notifyListeners(SessionStats stats) {
        try {
            listeners.orderedStream().forEach(listener -> {
                try {
                    listener.onSnapshot(stats);
                } catch (Exception e) {
                    log.warn("会话统计监听器执行失败：{}", e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("会话统计监听器加载失败：{}", e.getMessage());
        }
    }
}
