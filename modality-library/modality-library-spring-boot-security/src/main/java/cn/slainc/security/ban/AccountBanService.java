package cn.slainc.security.ban;

import cn.dev33.satoken.stp.StpLogic;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;
import cn.slainc.security.audit.SecurityAuditRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountBanService {

    public static final String BAN_SERVICE = "ban";

    private static final Logger log = LoggerFactory.getLogger(AccountBanService.class);

    private final SecurityAuditRecorder recorder;

    public AccountBanService(SecurityAuditRecorder recorder) {
        this.recorder = recorder;
    }

    public StpLogic logic(LoginType type) {
        return StpKit.of(type);
    }

    public void ban(LoginType type, Object loginId, long ttlSeconds) {
        StpKit.of(type).disable(loginId, BAN_SERVICE, ttlSeconds);
        log.warn("账号封禁：账号体系={}，账号={}，时长={}秒", type.value(), loginId, ttlSeconds);
    }

    public boolean isBanned(LoginType type, Object loginId) {
        return StpKit.of(type).isDisable(loginId, BAN_SERVICE);
    }

    public void unban(LoginType type, Object loginId) {
        StpKit.of(type).untieDisable(loginId, BAN_SERVICE);
        log.warn("账号解禁：账号体系={}，账号={}", type.value(), loginId);
    }

    public long getBanRemain(LoginType type, Object loginId) {
        return StpKit.of(type).getDisableTime(loginId, BAN_SERVICE);
    }
}
