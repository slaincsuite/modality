package cn.slainc.modules.audit.dto.online;

import cn.slainc.session.SessionInfo;
import cn.slainc.session.query.SessionTokens;

import java.time.LocalDateTime;
import java.util.Map;

public record OnlineSessionView(String sessionId, String loginType, String loginId, String tokenValue,
                                String deviceType, String deviceCategory, String deviceId, String clientIp,
                                String userAgent, LocalDateTime loginTime, LocalDateTime lastActiveTime,
                                long timeoutSeconds, long remainingSeconds, boolean active,
                                Map<String, Object> extended) {

    public static OnlineSessionView of(SessionInfo info, String sessionId) {
        return new OnlineSessionView(sessionId, info.loginType(), info.loginId(), SessionTokens.mask(info.tokenValue()),
                info.deviceType(), info.deviceCategory(), info.deviceId(), info.clientIp(), info.userAgent(),
                info.loginTime(), info.lastActiveTime(), info.timeoutSeconds(), info.remainingSeconds(),
                info.active(), info.extended());
    }
}
