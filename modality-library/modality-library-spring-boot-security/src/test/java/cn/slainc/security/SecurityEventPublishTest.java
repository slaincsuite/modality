package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.LoginEvent;
import cn.slainc.security.event.KickoutEvent;
import cn.slainc.security.event.PasswordLockedEvent;
import cn.slainc.security.event.SecurityAlarmEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEventPublishTest extends SecurityAcceptanceTestBase {

    @Test
    void loginSuccessPublishesLoginEvent() {
        login(LoginType.TENANT_USER, "user");
        assertThat(events.of(LoginEvent.class)).filteredOn(LoginEvent::isSucceeded).first()
                .satisfies(event -> {
                    assertThat(event.getUserId()).isEqualTo(2001L);
                    assertThat(event.getUsername()).isEqualTo("user");
                    assertThat(event.getLoginType()).isEqualTo(LoginType.TENANT_USER);
                });
    }

    @Test
    void loginFailurePublishesFailedLoginEvent() {
        post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"wrong-pass","deviceType":"","deviceId":""}
                """);
        assertThat(events.of(LoginEvent.class)).filteredOn(event -> !event.isSucceeded()).first()
                .satisfies(event -> assertThat(event.getFailureReason()).contains("密码错误"));
    }

    @Test
    void kickPublishesKickoutEventWithReason() {
        tokenOf(LoginType.TENANT_USER, "user");
        post("/test/admin/kick?type=TENANT_USER&loginId=2001&reason=事件测试", LoginType.PLATFORM_ADMIN,
                tokenOf(LoginType.PLATFORM_ADMIN, "admin"), null);
        assertThat(events.of(KickoutEvent.class)).first().satisfies(event -> {
            assertThat(event.getLoginId()).isEqualTo(2001L);
            assertThat(event.getReason()).isEqualTo("事件测试");
            assertThat(event.getLoginType()).isEqualTo(LoginType.TENANT_USER);
        });
    }

    @Test
    void lockoutPublishesPasswordLockedEvent() {
        config.set("modality.security.lockout.max-failures", "2");
        post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"bad1","deviceType":"","deviceId":""}
                """);
        post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"bad2","deviceType":"","deviceId":""}
                """);
        assertThat(events.of(PasswordLockedEvent.class)).first().satisfies(event -> {
            assertThat(event.getLoginId()).isEqualTo(2001L);
            assertThat(event.getFailCount()).isGreaterThanOrEqualTo(2);
        });
    }

    @Test
    void travelIpChangePublishesSecurityAlarmEventAndWarnLog() {
        ch.qos.logback.classic.Logger auditLogger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(cn.slainc.security.audit.SecurityAuditRecorder.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        try {
            login(LoginType.TENANT_USER, "user", PASSWORD, null, null, "8.8.1.1");
            events.clear();
            login(LoginType.TENANT_USER, "user", PASSWORD, null, null, "8.8.2.2");
            assertThat(events.of(SecurityAlarmEvent.class)).first().satisfies(event -> {
                assertThat(event.getAlarmType()).isEqualTo("异地登录");
                assertThat(event.getIp()).isEqualTo("8.8.2.2");
                assertThat(event.getLoginId()).isEqualTo(2001L);
            });
            assertThat(appender.list.stream()
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("异地登录") && message.contains("2001"))).isTrue();
        } finally {
            auditLogger.detachAppender(appender);
        }
    }

    @Test
    void sameIpReloginDoesNotAlarm() {
        login(LoginType.TENANT_USER, "user", PASSWORD, null, null, "9.9.9.9");
        events.clear();
        login(LoginType.TENANT_USER, "user", PASSWORD, null, null, "9.9.9.9");
        assertThat(events.of(SecurityAlarmEvent.class))
                .filteredOn(event -> "异地登录".equals(event.getAlarmType())).isEmpty();
    }

    @Test
    void countersAccumulateLoginOutcome() {
        long successBefore = counters.loginSuccess();
        long failureBefore = counters.loginFailure();
        login(LoginType.TENANT_USER, "user");
        post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"bad","deviceType":"","deviceId":""}
                """);
        assertThat(counters.loginSuccess()).isEqualTo(successBefore + 1);
        assertThat(counters.loginFailure()).isGreaterThanOrEqualTo(failureBefore + 1);
    }
}
