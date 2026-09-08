package cn.slainc.security.audit;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.LogoutEvent;
import cn.slainc.security.event.KickoutEvent;
import cn.slainc.security.event.PasswordLockedEvent;
import cn.slainc.security.event.SecurityAlarmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

public class SecurityAuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditRecorder.class);

    private final ApplicationEventPublisher publisher;

    public SecurityAuditRecorder(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void alarm(String alarmType, LoginType type, Object loginId, String ip, String reason) {
        log.warn("安全告警[{}]：账号体系={}，账号={}，IP={}，原因={}", alarmType, type == null ? "-" : type.value(),
                loginId, ip == null ? "-" : ip, reason);
        publisher.publishEvent(new SecurityAlarmEvent(alarmType, type, loginId, ip, reason));
    }

    public void kickout(LoginType type, Object loginId, String tokenValue, String deviceType, String reason) {
        log.warn("踢人下线：账号体系={}，账号={}，设备类型={}，原因={}", type == null ? "-" : type.value(),
                loginId, deviceType == null ? "-" : deviceType, reason);
        publisher.publishEvent(new KickoutEvent(type, loginId, tokenValue, deviceType, reason));
    }

    public void locked(LoginType type, Object loginId, int failCount) {
        log.warn("密码连续错误已锁定：账号体系={}，账号={}，失败次数={}，已达到锁定阈值", type == null ? "-" : type.value(),
                loginId, failCount);
        publisher.publishEvent(new PasswordLockedEvent(type, loginId, failCount));
    }

    public void logout(Long userId, String username, LoginType type, boolean forced) {
        publisher.publishEvent(new LogoutEvent(userId, username, type, forced));
    }
}
