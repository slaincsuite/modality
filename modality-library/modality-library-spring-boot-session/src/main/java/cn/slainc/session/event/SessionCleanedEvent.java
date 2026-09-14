package cn.slainc.session.event;

import cn.slainc.core.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class SessionCleanedEvent extends DomainEvent {

    private final String loginType;

    private final String loginId;

    private final String tokenValue;

    private final String reason;

    public SessionCleanedEvent(String loginType, String loginId, String tokenValue, String reason) {
        super("session-clean");
        this.loginType = loginType;
        this.loginId = loginId;
        this.tokenValue = tokenValue;
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

    public String getReason() {
        return reason;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loginType", loginType);
        payload.put("loginId", loginId);
        payload.put("tokenValue", tokenValue);
        payload.put("reason", reason);
        return payload;
    }
}
