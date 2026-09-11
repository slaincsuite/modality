package cn.slainc.session.kick;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.StpKit;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.session.SessionKickNotice;
import cn.slainc.session.event.SessionKickedEvent;
import cn.slainc.session.query.RawScanOutcome;
import cn.slainc.session.query.RawTerminal;
import cn.slainc.session.query.SessionScanner;
import cn.slainc.session.query.SessionTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KickSessionService {

    public static final String DEFAULT_REASON = "管理员强制下线";

    private static final Logger log = LoggerFactory.getLogger(KickSessionService.class);

    private final KickService kickService;

    private final SecurityLoginService loginService;

    private final SessionScanner scanner;

    private final ApplicationEventPublisher publisher;

    private final ObjectProvider<SessionKickNotifier> notifiers;

    public KickSessionService(KickService kickService, SecurityLoginService loginService, SessionScanner scanner,
                              ApplicationEventPublisher publisher, ObjectProvider<SessionKickNotifier> notifiers) {
        this.kickService = kickService;
        this.loginService = loginService;
        this.scanner = scanner;
        this.publisher = publisher;
        this.notifiers = notifiers;
    }

    public void kickout(LoginType type, Object loginId, String reason) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        if (loginId == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        List<String> tokens = tokensOf(type, loginId, null);
        kickService.kickout(type, loginId, normalize(reason));
        afterKick(type, String.valueOf(loginId), tokens, null, normalize(reason));
    }

    public void kickout(LoginType type, Object loginId, String deviceType, String reason) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        if (loginId == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        String normalizedReason = normalize(reason);
        List<String> tokens = tokensOf(type, loginId, deviceType);
        kickService.kickout(type, loginId, deviceType, normalizedReason);
        afterKick(type, String.valueOf(loginId), tokens, deviceType, normalizedReason);
    }

    public boolean kickoutByToken(LoginType type, String tokenValue, String reason) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        if (tokenValue == null || tokenValue.isBlank()) {
            return false;
        }
        Object loginId = StpKit.of(type).getLoginIdByToken(tokenValue);
        if (loginId == null) {
            log.warn("token 对应的会话不存在或已失效，跳过踢出：{}", SessionTokens.mask(tokenValue));
            return false;
        }
        String normalizedReason = normalize(reason);
        kickService.kickout(type, loginId, null, tokenValue, normalizedReason);
        afterKick(type, String.valueOf(loginId), List.of(tokenValue), null, normalizedReason);
        return true;
    }

    public int batchKickout(LoginType type, List<Long> loginIds, String reason) {
        if (loginIds == null || loginIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long loginId : loginIds) {
            if (loginId == null) {
                continue;
            }
            kickout(type, loginId, reason);
            count++;
        }
        return count;
    }

    public int kickoutByTenant(LoginType type, String tenantId, String reason) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("租户标识不能为空");
        }
        String normalizedReason = normalize(reason);
        int count = 0;
        for (RawScanOutcome outcome : scanner.scanRawAll(type)) {
            if (outcome.truncated()) {
                log.warn("租户 {} 的会话扫描被截断，仅处理已扫描到的 {} 条：{}", tenantId, outcome.scanned(),
                        outcome.message());
            }
            for (RawTerminal terminal : outcome.terminals()) {
                if (!tenantId.equals(tenantOf(terminal))) {
                    continue;
                }
                kickService.kickout(terminal.type(), terminal.loginId(), terminal.terminal().getDeviceType(),
                        terminal.tokenValue(), normalizedReason);
                afterKick(terminal.type(), terminal.loginId(), List.of(terminal.tokenValue()),
                        terminal.terminal().getDeviceType(), normalizedReason);
                count++;
            }
        }
        return count;
    }

    public int kickoutByDevice(LoginType type, Object loginId, String deviceId, String reason) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        if (loginId == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("设备标识不能为空");
        }
        List<SaTerminalInfo> terminals = StpKit.of(type).getTerminalListByLoginId(loginId);
        String normalizedReason = normalize(reason);
        int count = 0;
        for (SaTerminalInfo terminal : terminals) {
            if (!deviceId.equals(terminal.getDeviceId())) {
                continue;
            }
            kickService.kickout(type, loginId, terminal.getDeviceType(), terminal.getTokenValue(), normalizedReason);
            afterKick(type, String.valueOf(loginId), List.of(terminal.getTokenValue()),
                    terminal.getDeviceType(), normalizedReason);
            count++;
        }
        return count;
    }

    public int forceLogout(LoginType type, Object loginId) {
        return forceLogout(type, loginId, DEFAULT_REASON);
    }

    public int forceLogout(LoginType type, Object loginId, String reason) {
        if (type == null) {
            throw new IllegalArgumentException("账号体系不能为空");
        }
        if (loginId == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        String normalizedReason = normalize(reason);
        List<String> tokens = tokensOf(type, loginId, null);
        loginService.logout(type, loginId);
        afterKick(type, String.valueOf(loginId), tokens, null, normalizedReason);
        return tokens.size();
    }

    public String kickReason(LoginType type, String tokenValue) {
        return type == null || tokenValue == null ? null : kickService.kickReason(tokenValue);
    }

    private void afterKick(LoginType type, String loginId, List<String> tokens, String deviceType, String reason) {
        List<String> effective = tokens == null || tokens.isEmpty() ? Collections.singletonList(null) : tokens;
        for (String tokenValue : effective) {
            publisher.publishEvent(new SessionKickedEvent(type.value(), loginId, tokenValue, deviceType, reason));
            SessionKickNotice notice = new SessionKickNotice(type.value(), loginId,
                    tokenValue == null ? null : SessionTokens.mask(tokenValue), deviceType, reason, LocalDateTime.now());
            notifiers.orderedStream().forEach(notifier -> {
                try {
                    notifier.notify(notice);
                } catch (Exception e) {
                    log.warn("会话下线通知（{}）发送失败：{}", notifier.channel(), e.getMessage());
                }
            });
        }
    }

    private List<String> tokensOf(LoginType type, Object loginId, String deviceType) {
        List<String> tokens = new ArrayList<>();
        for (SaTerminalInfo terminal : StpKit.of(type).getTerminalListByLoginId(loginId, deviceType)) {
            if (terminal.getTokenValue() != null) {
                tokens.add(terminal.getTokenValue());
            }
        }
        return tokens;
    }

    private String tenantOf(RawTerminal terminal) {
        Object value = terminal.terminal().getExtra("tenantId");
        return value == null ? null : String.valueOf(value);
    }

    private String normalize(String reason) {
        return reason == null || reason.isBlank() ? DEFAULT_REASON : reason;
    }
}
