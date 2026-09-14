package cn.slainc.session.cleanup;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;
import cn.slainc.security.audit.SecurityAuditRecorder;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.stp.SecurityStpLogic;
import cn.slainc.session.config.SessionConfigReader;
import cn.slainc.session.event.SessionCleanedEvent;
import cn.slainc.session.event.SessionExpiredEvent;
import cn.slainc.session.query.RawScanOutcome;
import cn.slainc.session.query.RawTerminal;
import cn.slainc.session.query.SessionScanner;
import cn.slainc.session.query.SessionTokens;
import cn.slainc.session.timeout.SessionTimeoutPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

public class SessionCleanupService {

    public static final String EXPIRED_TERMINAL_REASON = "token 已失效，清理残留终端";

    public static final String IDLE_ALARM_TYPE = "会话空闲超时";

    public static final String LIFETIME_ALARM_TYPE = "会话绝对超时";

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupService.class);

    private final SessionConfigReader config;

    private final SessionTimeoutPolicy policy;

    private final SessionScanner scanner;

    private final KickService kickService;

    private final SecurityAuditRecorder recorder;

    private final ApplicationEventPublisher publisher;

    public SessionCleanupService(SessionConfigReader config, SessionTimeoutPolicy policy, SessionScanner scanner,
                                 KickService kickService, SecurityAuditRecorder recorder,
                                 ApplicationEventPublisher publisher) {
        this.config = config;
        this.policy = policy;
        this.scanner = scanner;
        this.kickService = kickService;
        this.recorder = recorder;
        this.publisher = publisher;
    }

    public CleanupReport cleanupExpired(LoginType type) {
        int removed = 0;
        boolean truncated = false;
        String message = null;
        for (LoginType item : scanner.typesOf(type)) {
            SecurityStpLogic logic = StpKit.of(item);
            int maxScan = config.maxScan();
            List<String> keys;
            try {
                keys = logic.searchSessionId("", 0, maxScan + 1, true);
            } catch (Exception e) {
                log.warn("清理 {} 体系过期会话时扫描账号会话失败：{}", item.value(), e.getMessage());
                continue;
            }
            if (keys == null || keys.isEmpty()) {
                continue;
            }
            if (keys.size() > maxScan) {
                truncated = true;
                message = config.truncatedMessage(maxScan);
                keys = new ArrayList<>(keys.subList(0, maxScan));
            }
            String prefix = logic.splicingKeySession("");
            for (String key : keys) {
                String loginId = SessionTokens.strip(key, prefix);
                SaSession session;
                try {
                    session = logic.getSessionByLoginId(loginId, false);
                } catch (Exception e) {
                    continue;
                }
                if (session == null) {
                    continue;
                }
                for (SaTerminalInfo terminal : session.terminalListCopy()) {
                    if (isTokenAlive(logic, terminal.getTokenValue())) {
                        continue;
                    }
                    session.removeTerminal(terminal.getTokenValue());
                    removed++;
                    publisher.publishEvent(new SessionCleanedEvent(item.value(), loginId, terminal.getTokenValue(),
                            EXPIRED_TERMINAL_REASON));
                    log.info("清理残留终端：账号体系={}，账号={}，设备类型={}", item.value(), loginId, terminal.getDeviceType());
                }
                if (session.getTerminalList().isEmpty()) {
                    try {
                        session.logout();
                    } catch (Exception e) {
                        log.warn("清理空的账号会话失败：{}", e.getMessage());
                    }
                }
            }
        }
        return new CleanupReport(removed, 0, 0, truncated, message);
    }

    public CleanupReport cleanupZombie(long idleSeconds) {
        if (idleSeconds <= 0) {
            return CleanupReport.empty();
        }
        int kicked = 0;
        boolean truncated = false;
        String message = null;
        for (RawScanOutcome outcome : scanner.scanRawAll(null)) {
            if (outcome.truncated()) {
                truncated = true;
                message = outcome.message();
            }
            for (RawTerminal terminal : outcome.terminals()) {
                long lastActive = lastActiveOf(terminal);
                long idleMillis = System.currentTimeMillis() - lastActive;
                if (idleMillis <= idleSeconds * 1000L) {
                    continue;
                }
                kickTimeout(terminal, SessionTimeoutPolicy.IDLE_REASON, IDLE_ALARM_TYPE, idleMillis / 1000L);
                kicked++;
            }
        }
        return new CleanupReport(0, kicked, 0, truncated, message);
    }

    public CleanupReport cleanupAbsoluteTimeout(LoginType type) {
        long maxLifetime = policy.maxLifetimeSeconds();
        if (maxLifetime <= 0) {
            return CleanupReport.empty();
        }
        int kicked = 0;
        boolean truncated = false;
        String message = null;
        for (RawScanOutcome outcome : scanner.scanRawAll(type)) {
            if (outcome.truncated()) {
                truncated = true;
                message = outcome.message();
            }
            for (RawTerminal terminal : outcome.terminals()) {
                long loginTime = loginTimeOf(terminal);
                long elapsed = (System.currentTimeMillis() - loginTime) / 1000L;
                if (elapsed <= maxLifetime) {
                    continue;
                }
                kickTimeout(terminal, SessionTimeoutPolicy.LIFETIME_REASON, LIFETIME_ALARM_TYPE, elapsed);
                kicked++;
            }
        }
        return new CleanupReport(0, 0, kicked, truncated, message);
    }

    public CleanupReport cleanupAll() {
        CleanupReport report = cleanupExpired(null);
        long idleSeconds = config.idleTimeoutSeconds() > 0 ? config.idleTimeoutSeconds() : config.zombieIdleSeconds();
        report = report.merge(cleanupZombie(idleSeconds));
        if (config.cleanupAbsoluteTimeoutEnabled()) {
            report = report.merge(cleanupAbsoluteTimeout(null));
        }
        return report;
    }

    private void kickTimeout(RawTerminal terminal, String reason, String alarmType, long elapsedSeconds) {
        kickService.kickout(terminal.type(), terminal.loginId(), terminal.terminal().getDeviceType(),
                terminal.tokenValue(), reason);
        recorder.alarm(alarmType, terminal.type(), terminal.loginId(), ipOf(terminal), reason + "（已持续 "
                + elapsedSeconds + " 秒）");
        publisher.publishEvent(new SessionExpiredEvent(terminal.type().value(), terminal.loginId(),
                terminal.tokenValue(), alarmType, elapsedSeconds, reason));
    }

    private boolean isTokenAlive(SecurityStpLogic logic, String tokenValue) {
        if (tokenValue == null) {
            return false;
        }
        try {
            return logic.getLoginIdByToken(tokenValue) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private long lastActiveOf(RawTerminal terminal) {
        try {
            long stored = StpKit.of(terminal.type()).getTokenLastActiveTime(terminal.tokenValue());
            if (stored > 0) {
                return stored;
            }
        } catch (Exception e) {
            log.debug("读取最后活跃时间失败：{}", e.getMessage());
        }
        return loginTimeOf(terminal);
    }

    private long loginTimeOf(RawTerminal terminal) {
        long loginTime = SessionScanner.asLong(terminal.terminal().getExtra("loginTime"), 0L);
        if (loginTime > 0) {
            return loginTime;
        }
        return terminal.terminal().getCreateTime() > 0 ? terminal.terminal().getCreateTime()
                : System.currentTimeMillis();
    }

    private String ipOf(RawTerminal terminal) {
        return SessionScanner.asString(terminal.terminal().getExtra("ip"));
    }
}
