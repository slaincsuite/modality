package cn.slainc.core.event;

import cn.slainc.core.enums.LoginType;

import java.util.LinkedHashMap;
import java.util.Map;

public class LogoutEvent extends DomainEvent {

    private final Long userId;

    private final String username;

    private final LoginType loginType;

    private final boolean forced;

    public LogoutEvent(Long userId, String username, LoginType loginType, boolean forced) {
        super("auth");
        this.userId = userId;
        this.username = username;
        this.loginType = loginType;
        this.forced = forced;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public LoginType getLoginType() {
        return loginType;
    }

    public boolean isForced() {
        return forced;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("loginType", loginType);
        payload.put("forced", forced);
        return payload;
    }
}
