package cn.slainc.session.query;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;
import cn.slainc.security.stp.SecurityStpLogic;
import cn.slainc.session.SessionInfo;
import cn.slainc.session.device.DeviceTypeNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SessionQueryService {

    private final SessionScanner scanner;

    public SessionQueryService(SessionScanner scanner) {
        this.scanner = scanner;
    }

    public List<SessionInfo> listByLoginId(LoginType type, Object loginId) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        if (loginId == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        SecurityStpLogic logic = StpKit.of(type);
        String loginIdText = String.valueOf(loginId);
        List<SaTerminalInfo> terminals = logic.getTerminalListByLoginId(loginId);
        List<SessionInfo> sessions = new ArrayList<>(terminals.size());
        for (SaTerminalInfo terminal : terminals) {
            String tokenValue = terminal.getTokenValue();
            sessions.add(scanner.describe(type, loginIdText, terminal, tokenValue, isActive(logic, tokenValue)));
        }
        sessions.sort(Comparator.comparing(SessionInfo::loginTime,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return sessions;
    }

    public SessionInfo detail(LoginType type, String tokenValue) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        if (tokenValue == null || tokenValue.isBlank()) {
            return null;
        }
        SecurityStpLogic logic = StpKit.of(type);
        Object loginId = logic.getLoginIdByToken(tokenValue);
        if (loginId == null) {
            return null;
        }
        SaSession session = logic.getSessionByLoginId(loginId, false);
        if (session == null) {
            return null;
        }
        SaTerminalInfo terminal = session.getTerminal(tokenValue);
        if (terminal == null) {
            return null;
        }
        return scanner.describe(type, String.valueOf(loginId), terminal, tokenValue, true);
    }

    public List<SessionInfo> byDeviceId(LoginType type, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("设备标识不能为空");
        }
        List<SessionInfo> result = new ArrayList<>();
        for (SessionInfo session : listAll(type)) {
            if (deviceId.equals(session.deviceId())) {
                result.add(session);
            }
        }
        return result;
    }

    public List<SessionInfo> byDeviceType(LoginType type, String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            throw new IllegalArgumentException("设备类型不能为空");
        }
        List<SessionInfo> result = new ArrayList<>();
        for (SessionInfo session : listAll(type)) {
            if (DeviceTypeNormalizer.matches(session.deviceType(), session.userAgent(), deviceType)) {
                result.add(session);
            }
        }
        return result;
    }

    public List<SessionInfo> listAll(LoginType type) {
        List<SessionInfo> result = new ArrayList<>();
        for (SessionScanOutcome outcome : scanner.scanAll(type)) {
            result.addAll(outcome.sessions());
        }
        return result;
    }

    public List<SessionScanOutcome> scanOutcomes(LoginType type) {
        return scanner.scanAll(type);
    }

    private boolean isActive(SecurityStpLogic logic, String tokenValue) {
        if (tokenValue == null) {
            return false;
        }
        try {
            return logic.getLoginIdByToken(tokenValue) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
