package cn.slainc.security.event;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class PasswordLockedEvent extends DomainEvent {

    private final LoginType loginType;

    private final Object loginId;

    private final int failCount;

    public PasswordLockedEvent(LoginType loginType, Object loginId, int failCount) {
        super("security-lock");
        this.loginType = loginType;
        this.loginId = loginId;
        this.failCount = failCount;
    }

    public LoginType getLoginType() {
        return loginType;
    }

    public Object getLoginId() {
        return loginId;
    }

    public int getFailCount() {
        return failCount;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("loginType", loginType);
        payload.put("loginId", loginId);
        payload.put("failCount", failCount);
        return payload;
    }
}
