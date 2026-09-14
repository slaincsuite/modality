package cn.slainc.session.event;

import cn.slainc.core.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class SessionExpiredEvent extends DomainEvent {

    private final String loginType;

    private final String loginId;

    private final String tokenValue;

    private final String expireType;

    private final long elapsedSeconds;

    private final String reason;

    public SessionExpiredEvent(String loginType, String loginId, String tokenValue, String expireType,
                               long elapsedSeconds, String reason) {
        super("session-expire");
        this.loginType = loginType;
        this.loginId = loginId;
        this.tokenValue = tokenValue;
        this.expireType = expireType;
        this.elapsedSeconds = elapsedSeconds;
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

    public String getExpireType() {
        return expireType;
    }

    public long getElapsedSeconds() {
        return elapsedSeconds;
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
        payload.put("expireType", expireType);
        payload.put("elapsedSeconds", elapsedSeconds);
        payload.put("reason", reason);
        return payload;
    }
}
