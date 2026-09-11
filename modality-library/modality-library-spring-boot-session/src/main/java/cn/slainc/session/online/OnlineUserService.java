package cn.slainc.session.online;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.PageResult;
import cn.slainc.session.OnlineQuery;
import cn.slainc.session.OnlineUserPage;
import cn.slainc.session.SessionInfo;
import cn.slainc.session.config.SessionConfigReader;
import cn.slainc.session.query.SessionScanOutcome;
import cn.slainc.session.query.SessionScanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OnlineUserService {

    private final SessionScanner scanner;

    private final SessionConfigReader config;

    private final SessionCsvWriter csvWriter = new SessionCsvWriter();

    public OnlineUserService(SessionScanner scanner, SessionConfigReader config) {
        this.scanner = scanner;
        this.config = config;
    }

    public PageResult<SessionInfo> page(OnlineQuery query) {
        return pageWithSummary(query).page();
    }

    public OnlineUserPage pageWithSummary(OnlineQuery query) {
        OnlineQuery safe = query == null ? OnlineQuery.all() : query;
        List<SessionScanOutcome> outcomes = scanner.scanAll(safe.type());
        boolean truncated = false;
        String message = null;
        int scanned = 0;
        List<SessionInfo> matched = new ArrayList<>();
        for (SessionScanOutcome outcome : outcomes) {
            scanned += outcome.scanned();
            if (outcome.truncated()) {
                truncated = true;
                if (message == null) {
                    message = outcome.message();
                }
            }
            for (SessionInfo session : outcome.sessions()) {
                if (matches(safe, session)) {
                    matched.add(session);
                }
            }
        }
        matched.sort(comparator(safe));
        PageResult<SessionInfo> page = PageResult.from(matched, safe.page(), safe.size());
        return new OnlineUserPage(page, truncated, message, scanned, config.maxScan());
    }

    public List<SessionInfo> list(OnlineQuery query) {
        return pageWithSummary(query).page().records();
    }

    public long onlineCount(LoginType type) {
        long total = 0;
        for (SessionScanOutcome outcome : scanner.scanAll(type)) {
            total += outcome.sessions().size();
        }
        return total;
    }

    public Map<String, Long> countByDeviceType() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (SessionInfo session : all()) {
            counts.merge(session.deviceCategory(), 1L, Long::sum);
        }
        return counts;
    }

    public Map<String, Long> countByTenant() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (SessionInfo session : all()) {
            counts.merge(session.tenantKey(), 1L, Long::sum);
        }
        return counts;
    }

    public Map<String, Long> countByType() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (SessionInfo session : all()) {
            counts.merge(session.loginType(), 1L, Long::sum);
        }
        return counts;
    }

    public String exportCsv(OnlineQuery query) {
        OnlineQuery safe = query == null ? OnlineQuery.all() : query;
        long total = pageWithSummary(withSize(safe)).total();
        OnlineQuery exportQuery = withSize(safe, total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total);
        return csvWriter.write(pageWithSummary(exportQuery).page().records());
    }

    public String exportCsv(OnlineQuery query, int maxRows) {
        OnlineQuery safe = query == null ? OnlineQuery.all() : query;
        int size = maxRows <= 0 ? OnlineQuery.DEFAULT_SIZE : maxRows;
        return csvWriter.write(pageWithSummary(withSize(safe, size)).page().records());
    }

    private OnlineQuery withSize(OnlineQuery query) {
        return withSize(query, OnlineQuery.MAX_SIZE);
    }

    private OnlineQuery withSize(OnlineQuery query, int size) {
        return new OnlineQuery(query.type(), query.tenantId(), query.deviceType(), query.loginAfter(),
                query.loginBefore(), query.keyword(), 1, size, query.sortField(), query.descending());
    }

    private List<SessionInfo> all() {
        List<SessionInfo> sessions = new ArrayList<>();
        for (SessionScanOutcome outcome : scanner.scanAll(null)) {
            sessions.addAll(outcome.sessions());
        }
        return sessions;
    }

    private boolean matches(OnlineQuery query, SessionInfo session) {
        if (query.tenantId() != null) {
            String tenant = query.tenantId();
            if (tenant.isEmpty()) {
                if (session.tenantKey() != null) {
                    return false;
                }
            } else if (!tenant.equals(session.tenantKey())) {
                return false;
            }
        }
        if (query.deviceType() != null && !query.deviceType().isBlank()
                && !cn.slainc.session.device.DeviceTypeNormalizer.matches(session.deviceType(),
                session.userAgent(), query.deviceType())) {
            return false;
        }
        if (query.loginAfter() != null && (session.loginTime() == null || session.loginTime().isBefore(query.loginAfter()))) {
            return false;
        }
        if (query.loginBefore() != null && (session.loginTime() == null || session.loginTime().isAfter(query.loginBefore()))) {
            return false;
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String keyword = query.keyword().trim();
            boolean hit = contains(session.loginId(), keyword) || contains(session.tokenValue(), keyword)
                    || contains(session.deviceId(), keyword);
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    private Comparator<SessionInfo> comparator(OnlineQuery query) {
        Comparator<SessionInfo> comparator = switch (query.sortField()) {
            case LOGIN_TIME -> Comparator.comparing(SessionInfo::loginTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case LAST_ACTIVE_TIME -> Comparator.comparing(SessionInfo::lastActiveTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case LOGIN_ID -> Comparator.comparing(SessionInfo::loginId,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case DEVICE_TYPE -> Comparator.comparing(SessionInfo::deviceCategory,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return query.descending() ? comparator.reversed() : comparator;
    }
}
