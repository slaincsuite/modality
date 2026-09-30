package cn.slainc.modules.audit.log;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.core.event.ConfigChangedEvent;
import cn.slainc.core.event.ConsentGrantedEvent;
import cn.slainc.core.event.LoginEvent;
import cn.slainc.core.event.LogoutEvent;
import cn.slainc.modules.audit.AuditModuleApplication;
import cn.slainc.session.event.SessionKickedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 事件桥与 @AuditLog 落库：五类领域事件 + 切面审计都应写入 t_audit_log
@SpringBootTest(classes = AuditModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditEventModuleTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        jdbc.execute("DELETE FROM t_audit_log");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void successAndFailureLoginEventsWriteAuditLog() {
        publisher.publishEvent(LoginEvent.success(91001L, "auditor", LoginType.TENANT_USER, "127.0.0.1"));
        publisher.publishEvent(LoginEvent.failure("intruder", LoginType.TENANT_USER, "10.0.0.9", "密码错误"));
        Integer success = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE category = 'LOGIN' AND operation = 'LOGIN'"
                        + " AND username = 'auditor' AND success = 1", Integer.class);
        Integer failure = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE category = 'LOGIN' AND operation = 'LOGIN'"
                        + " AND username = 'intruder' AND success = 0 AND level = 'WARN'"
                        + " AND description LIKE '%密码错误%'", Integer.class);
        assertThat(success).isEqualTo(1);
        assertThat(failure).isEqualTo(1);
    }

    @Test
    void logoutEventWritesAuditLog() {
        publisher.publishEvent(new LogoutEvent(91001L, "auditor", LoginType.TENANT_USER, false));
        publisher.publishEvent(new LogoutEvent(91001L, "auditor", LoginType.TENANT_USER, true));
        Integer voluntary = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE category = 'LOGIN' AND operation = 'LOGOUT'"
                        + " AND description LIKE '%主动登出%'", Integer.class);
        Integer forced = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE category = 'LOGIN' AND operation = 'LOGOUT'"
                        + " AND description LIKE '%强制登出%'", Integer.class);
        assertThat(voluntary).isEqualTo(1);
        assertThat(forced).isEqualTo(1);
    }

    @Test
    void consentAndConfigEventsWriteAuditLog() {
        publisher.publishEvent(new ConsentGrantedEvent(91001L, "client-a", Set.of("userinfo", "pay")));
        publisher.publishEvent(new ConfigChangedEvent("modality.audit.cleanup.retention-days", "audit", "90",
                "30", false));
        Integer consent = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE category = 'PERMISSION' AND operation = 'GRANT'"
                        + " AND target = 'client-a' AND result LIKE '%pay%'", Integer.class);
        Integer config = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE category = 'SYSTEM' AND operation = 'CONFIG'"
                        + " AND before_snapshot = '90' AND after_snapshot = '30'", Integer.class);
        assertThat(consent).isEqualTo(1);
        assertThat(config).isEqualTo(1);
    }

    @Test
    void sessionKickedEventWritesAuditLog() {
        publisher.publishEvent(new SessionKickedEvent(LoginType.TENANT_USER.value(), "91001",
                "token-value-a", "WEB", "管理员强制下线"));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE category = 'SECURITY' AND user_id = 91001"
                        + " AND description LIKE '%管理员强制下线%'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void annotatedEndpointWritesAuditLogThroughDispatcher() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit-test/annotated")).GET()
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        // 审计切面经异步分发器落库，稍候片刻再断言
        Thread.sleep(800);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE description = '演示操作' AND operation = 'EXPORT'"
                        + " AND category = 'DATA'", Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
