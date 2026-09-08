package cn.slainc.security;

import cn.slainc.core.event.LoginEvent;
import cn.slainc.security.event.KickoutEvent;
import cn.slainc.security.event.PasswordLockedEvent;
import cn.slainc.security.event.SecurityAlarmEvent;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TestEventCollector {

    private final List<Object> received = new CopyOnWriteArrayList<>();

    @EventListener
    public void onLogin(LoginEvent event) {
        received.add(event);
    }

    @EventListener
    public void onKickout(KickoutEvent event) {
        received.add(event);
    }

    @EventListener
    public void onPasswordLocked(PasswordLockedEvent event) {
        received.add(event);
    }

    @EventListener
    public void onSecurityAlarm(SecurityAlarmEvent event) {
        received.add(event);
    }

    public <T> List<T> of(Class<T> type) {
        return received.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public void clear() {
        received.clear();
    }
}
