package cn.slainc.security.event;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class KickoutEvent extends DomainEvent {

    private final LoginType loginType;

    private final Object loginId;

    private final String tokenValue;

    private final String deviceType;

    private final String reason;

    public KickoutEvent(LoginType loginType, Object loginId, String tokenValue, String deviceType, String reason) {
        super("security-kick");
        this.loginType = loginType;
        this.loginId = loginId;
        this.tokenValue = tokenValue;
        this.deviceType = deviceType;
        this.reason = reason;
    }

    public LoginType getLoginType() {
        return loginType;
    }

    public Object getLoginId() {
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
