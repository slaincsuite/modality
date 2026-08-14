package cn.slainc.core.event;

import cn.slainc.core.enums.LoginType;

import java.util.LinkedHashMap;
import java.util.Map;

public class LoginEvent extends DomainEvent {

    private final Long userId;

    private final String username;

    private final LoginType loginType;

    private final String clientIp;

    private final boolean succeeded;

    private final String failureReason;

    public LoginEvent(Long userId, String username, LoginType loginType, String clientIp, boolean succeeded,
                      String failureReason) {
        super("auth");
        this.userId = userId;
        this.username = username;
        this.loginType = loginType;
        this.clientIp = clientIp;
        this.succeeded = succeeded;
        this.failureReason = failureReason;
    }

    public static LoginEvent success(Long userId, String username, LoginType loginType, String clientIp) {
        return new LoginEvent(userId, username, loginType, clientIp, true, null);
    }

    public static LoginEvent failure(String username, LoginType loginType, String clientIp, String reason) {
        return new LoginEvent(null, username, loginType, clientIp, false, reason);
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

    public String getClientIp() {
        return clientIp;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public String getFailureReason() {
        return failureReason;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("loginType", loginType);
        payload.put("clientIp", clientIp);
        payload.put("succeeded", succeeded);
        payload.put("failureReason", failureReason);
        return payload;
    }
}
