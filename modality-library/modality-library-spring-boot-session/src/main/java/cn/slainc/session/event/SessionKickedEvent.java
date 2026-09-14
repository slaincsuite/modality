package cn.slainc.session.event;

import cn.slainc.core.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class SessionKickedEvent extends DomainEvent {

    private final String loginType;

    private final String loginId;

    private final String tokenValue;

    private final String deviceType;

    private final String reason;

    public SessionKickedEvent(String loginType, String loginId, String tokenValue, String deviceType, String reason) {
        super("session-kick");
        this.loginType = loginType;
        this.loginId = loginId;
        this.tokenValue = tokenValue;
        this.deviceType = deviceType;
        this.reason = reason;
    }

    public String getLoginType() {
        return loginType;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getReason() {
        return reason;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loginType", loginType);
        payload.put("loginId", loginId);
        payload.put("tokenValue", tokenValue);
        payload.put("deviceType", deviceType);
        payload.put("reason", reason);
        return payload;
    }
}
