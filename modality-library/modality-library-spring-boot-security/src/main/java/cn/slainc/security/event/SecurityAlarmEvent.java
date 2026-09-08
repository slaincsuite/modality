package cn.slainc.security.event;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.DomainEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class SecurityAlarmEvent extends DomainEvent {

    private final String alarmType;

    private final LoginType loginType;

    private final Object loginId;

    private final String ip;

    private final String reason;

    public SecurityAlarmEvent(String alarmType, LoginType loginType, Object loginId, String ip, String reason) {
        super("security-alarm");
        this.alarmType = alarmType;
        this.loginType = loginType;
        this.loginId = loginId;
        this.ip = ip;
        this.reason = reason;
    }

    public String getAlarmType() {
        return alarmType;
    }

    public LoginType getLoginType() {
        return loginType;
    }

    public Object getLoginId() {
        return loginId;
    }

    public String getIp() {
        return ip;
    }

    public String getReason() {
        return reason;
    }

    @Override
    protected Object payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmType", alarmType);
        payload.put("loginType", loginType);
        payload.put("loginId", loginId);
        payload.put("ip", ip);
        payload.put("reason", reason);
        return payload;
    }
}
