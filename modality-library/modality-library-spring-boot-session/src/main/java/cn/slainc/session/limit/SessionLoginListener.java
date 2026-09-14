package cn.slainc.session.limit;

import cn.slainc.core.event.LoginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

public class SessionLoginListener {

    private static final Logger log = LoggerFactory.getLogger(SessionLoginListener.class);

    private final SessionLimitService limitService;

    public SessionLoginListener(SessionLimitService limitService) {
        this.limitService = limitService;
    }

    @EventListener
    public void onLogin(LoginEvent event) {
        if (event == null || !event.isSucceeded() || event.getUserId() == null || event.getLoginType() == null) {
            return;
        }
        try {
            limitService.enforce(event.getLoginType(), event.getUserId());
        } catch (Exception e) {
            log.warn("登录后校验会话数量上限失败：{}", e.getMessage());
        }
    }
}
