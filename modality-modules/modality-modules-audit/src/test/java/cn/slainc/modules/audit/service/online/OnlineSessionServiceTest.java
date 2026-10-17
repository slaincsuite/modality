package cn.slainc.modules.audit.service.online;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.audit.AuditKeys;
import cn.slainc.modules.audit.dto.online.OnlineSessionView;
import cn.slainc.modules.audit.dto.online.OnlineUserPageView;
import cn.slainc.modules.audit.support.online.SessionIdentifier;
import cn.slainc.modules.audit.web.online.OnlineUserAdminController;
import cn.slainc.security.annotation.RequirePermission;
import cn.slainc.session.OnlineQuery;
import cn.slainc.session.SessionInfo;
import cn.slainc.session.SessionSortField;
import cn.slainc.session.config.SessionConfigReader;
import cn.slainc.session.kick.KickSessionService;
import cn.slainc.session.query.RawScanOutcome;
import cn.slainc.session.query.RawTerminal;
import cn.slainc.session.query.SessionScanner;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnlineSessionServiceTest {

    private final SessionScanner scanner = mock(SessionScanner.class);

    private final SessionConfigReader config = mock(SessionConfigReader.class);

    private final KickSessionService kickService = mock(KickSessionService.class);

    private final OnlineSessionService service = new OnlineSessionService(scanner, config, kickService);

    @Test
    void keywordMatchesClientIpAndTokenRemainsMasked() {
        String rawToken = "a1b2c3d4e5f6g7h8i9j0";
        RawTerminal terminal = terminal(rawToken);
        SessionInfo info = info(rawToken, "10.0.0.8");
        when(config.maxScan()).thenReturn(100);
        when(scanner.scanRawAll(LoginType.TENANT_USER))
                .thenReturn(List.of(new RawScanOutcome(LoginType.TENANT_USER, List.of(terminal), false, null, 1)));
        when(scanner.describe(eq(LoginType.TENANT_USER), eq("1001"), any(SaTerminalInfo.class), eq(rawToken),
                eq(true), any(), any())).thenReturn(info);
        OnlineQuery query = new OnlineQuery(LoginType.TENANT_USER, null, null, null, null, "10.0.0.8", 1, 20,
                SessionSortField.LOGIN_TIME, true);

        OnlineUserPageView result = service.page(query);

        assertThat(result.page().total()).isEqualTo(1);
        OnlineSessionView session = result.page().records().get(0);
        assertThat(session.clientIp()).isEqualTo("10.0.0.8");
        assertThat(session.tokenValue()).isNotEqualTo(rawToken).contains("*");
        assertThat(session.sessionId()).startsWith("sid_").doesNotContain(rawToken);
    }

    @Test
    void exactKickUsesTheRawTokenForTheMatchedSessionOnly() {
        String rawToken = "session-token-1001";
        RawTerminal terminal = terminal(rawToken);
        String sessionId = SessionIdentifier.of(LoginType.TENANT_USER, rawToken);
        when(scanner.scanRawAll(LoginType.TENANT_USER))
                .thenReturn(List.of(new RawScanOutcome(LoginType.TENANT_USER, List.of(terminal), false, null, 1)));
        when(kickService.kickoutByToken(LoginType.TENANT_USER, rawToken, "管理员下线")).thenReturn(true);

        boolean kicked = service.kickBySessionId(LoginType.TENANT_USER, sessionId, "管理员下线");

        assertThat(kicked).isTrue();
        verify(kickService).kickoutByToken(LoginType.TENANT_USER, rawToken, "管理员下线");
    }

    @Test
    void unknownSessionIdIsNotKicked() {
        when(scanner.scanRawAll(LoginType.TENANT_USER))
                .thenReturn(List.of(new RawScanOutcome(LoginType.TENANT_USER,
                        List.of(terminal("another-token")), false, null, 1)));

        assertThat(service.kickBySessionId(LoginType.TENANT_USER,
                SessionIdentifier.of(LoginType.TENANT_USER, "not-online"), "管理员下线")).isFalse();
    }

    @Test
    void onlineEndpointsDeclareQueryExportAndKickPermissions() {
        assertThat(permissions("page")).containsExactly(AuditKeys.PERMISSION_ONLINE_QUERY);
        assertThat(permissions("export")).containsExactly(AuditKeys.PERMISSION_ONLINE_EXPORT);
        assertThat(permissions("kickSession")).containsExactly(AuditKeys.PERMISSION_ONLINE_KICK);
    }

    private String[] permissions(String methodName) {
        Method method = Arrays.stream(OnlineUserAdminController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        RequirePermission permission = method.getAnnotation(RequirePermission.class);
        assertThat(permission).as("端点 %s 必须声明权限", methodName).isNotNull();
        return permission.permissions();
    }

    private RawTerminal terminal(String token) {
        return new RawTerminal(LoginType.TENANT_USER, "1001", token, mock(SaTerminalInfo.class),
                System.currentTimeMillis(), 3600L);
    }

    private SessionInfo info(String token, String clientIp) {
        return new SessionInfo(LoginType.TENANT_USER.value(), "1001", token, "PC", "PC", "device-1",
                clientIp, "Mozilla/5.0", LocalDateTime.now().minusMinutes(5), LocalDateTime.now(), 7200L,
                3600L, true, Map.of("tenantId", 1L));
    }
}
