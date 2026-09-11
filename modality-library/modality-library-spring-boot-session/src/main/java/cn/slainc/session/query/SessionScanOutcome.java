package cn.slainc.session.query;

import cn.slainc.core.enums.LoginType;
import cn.slainc.session.SessionInfo;

import java.util.ArrayList;
import java.util.List;

public record SessionScanOutcome(LoginType type, List<SessionInfo> sessions, boolean truncated, String message,
                                 int scanned) {

    public SessionScanOutcome {
        sessions = sessions == null ? new ArrayList<>() : new ArrayList<>(sessions);
    }
}
