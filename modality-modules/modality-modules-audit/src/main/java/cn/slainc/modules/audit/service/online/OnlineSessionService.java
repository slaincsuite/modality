package cn.slainc.modules.audit.service.online;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.PageResult;
import cn.slainc.modules.audit.dto.online.OnlineSessionView;
import cn.slainc.modules.audit.dto.online.OnlineUserPageView;
import cn.slainc.modules.audit.support.online.SessionIdentifier;
import cn.slainc.session.OnlineQuery;
import cn.slainc.session.SessionInfo;
import cn.slainc.session.SessionSortField;
import cn.slainc.session.config.SessionConfigReader;
import cn.slainc.session.device.DeviceTypeNormalizer;
import cn.slainc.session.kick.KickSessionService;
import cn.slainc.session.query.RawScanOutcome;
import cn.slainc.session.query.RawTerminal;
import cn.slainc.session.query.SessionScanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class OnlineSessionService {

    private final SessionScanner scanner;

    private final SessionConfigReader config;

    private final KickSessionService kickService;

    public OnlineSessionService(SessionScanner scanner, SessionConfigReader config, KickSessionService kickService) {
        this.scanner = scanner;
        this.config = config;
        this.kickService = kickService;
    }

    public OnlineUserPageView page(OnlineQuery query) {
        OnlineQuery safe = query == null ? OnlineQuery.all() : query;
        ScanResult result = scan(safe);
        PageResult<OnlineSessionView> page = PageResult.from(result.sessions(), safe.page(), safe.size());
        return new OnlineUserPageView(page, result.truncated(), result.message(), result.scanned(),
                result.maxScan());
    }

    public List<OnlineSessionView> list(OnlineQuery query) {
        OnlineQuery safe = query == null ? OnlineQuery.all() : query;
        return PageResult.from(scan(safe).sessions(), 1, safe.size()).records();
    }

    public boolean kickBySessionId(LoginType type, String sessionId, String reason) {
        for (RawScanOutcome outcome : scanner.scanRawAll(type)) {
            for (RawTerminal terminal : outcome.terminals()) {
                if (SessionIdentifier.matches(sessionId, terminal.type(), terminal.tokenValue())) {
                    return kickService.kickoutByToken(terminal.type(), terminal.tokenValue(), reason);
                }
            }
        }
        return false;
    }

    private ScanResult scan(OnlineQuery query) {
        OnlineQuery safe = query == null ? OnlineQuery.all() : query;
        List<OnlineSessionView> matched = new ArrayList<>();
        boolean truncated = false;
        String message = null;
        int scanned = 0;
        for (RawScanOutcome outcome : scanner.scanRawAll(safe.type())) {
            scanned += outcome.scanned();
            if (outcome.truncated()) {
                truncated = true;
                if (message == null) {
                    message = outcome.message();
                }
            }
            for (RawTerminal terminal : outcome.terminals()) {
                SessionInfo info = scanner.describe(terminal.type(), terminal.loginId(), terminal.terminal(),
                        terminal.tokenValue(), true, terminal.lastActiveMillis(), terminal.remainingSeconds());
                OnlineSessionView session = OnlineSessionView.of(info,
                        SessionIdentifier.of(terminal.type(), terminal.tokenValue()));
                if (matches(safe, session)) {
                    matched.add(session);
                }
            }
        }
        matched.sort(comparator(safe));
        return new ScanResult(matched, truncated, message, scanned, config.maxScan());
    }

    private boolean matches(OnlineQuery query, OnlineSessionView session) {
        if (query.tenantId() != null) {
            Object tenantValue = session.extended().get("tenantId");
            if (query.tenantId().isEmpty()) {
                if (tenantValue != null) {
                    return false;
                }
            } else if (tenantValue == null || !query.tenantId().equals(String.valueOf(tenantValue))) {
                return false;
            }
        }
        if (query.deviceType() != null && !query.deviceType().isBlank()
                && !DeviceTypeNormalizer.matches(session.deviceType(), session.userAgent(), query.deviceType())) {
            return false;
        }
        if (query.loginAfter() != null
                && (session.loginTime() == null || session.loginTime().isBefore(query.loginAfter()))) {
            return false;
        }
        if (query.loginBefore() != null
                && (session.loginTime() == null || session.loginTime().isAfter(query.loginBefore()))) {
            return false;
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String keyword = query.keyword().trim();
            return contains(session.loginId(), keyword)
                    || contains(session.tokenValue(), keyword)
                    || contains(session.deviceId(), keyword)
                    || contains(session.clientIp(), keyword);
        }
        return true;
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    private Comparator<OnlineSessionView> comparator(OnlineQuery query) {
        Comparator<OnlineSessionView> comparator = switch (query.sortField()) {
            case LOGIN_TIME -> Comparator.comparing(OnlineSessionView::loginTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case LAST_ACTIVE_TIME -> Comparator.comparing(OnlineSessionView::lastActiveTime,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case LOGIN_ID -> Comparator.comparing(OnlineSessionView::loginId,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case DEVICE_TYPE -> Comparator.comparing(OnlineSessionView::deviceCategory,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return query.descending() ? comparator.reversed() : comparator;
    }

    private record ScanResult(List<OnlineSessionView> sessions, boolean truncated, String message, int scanned,
                              int maxScan) {
    }
}
