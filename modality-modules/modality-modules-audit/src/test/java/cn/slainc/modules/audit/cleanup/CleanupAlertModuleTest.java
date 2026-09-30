package cn.slainc.modules.audit.cleanup;

import cn.slainc.core.context.TenantContext;
import cn.slainc.modules.audit.AuditModuleApplication;
import cn.slainc.modules.audit.alert.AuditAlertChannel;
import cn.slainc.modules.audit.alert.AuditAlertMessage;
import cn.slainc.modules.audit.service.AuditAlertService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

// 清理（保留天数 + 手动触发）与告警评估（阈值 + 渠道通知）
@SpringBootTest(classes = {AuditModuleApplication.class, CleanupAlertModuleTest.AlertTestConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CleanupAlertModuleTest {

    @Autowired
    private AuditAlertService alertService;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private HttpClient http;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.set(1L);
        jdbc.execute("DELETE FROM t_audit_log");
        jdbc.execute("DELETE FROM t_audit_api_log");
        HttpResponse<String> response = http().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit-test/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        token = JsonPath.read(response.body(), "$.data");
        http = http();
    }

    private HttpClient http() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void manualCleanupDeletesExpiredRows() throws Exception {
        long stale = System.currentTimeMillis() - 100L * 24 * 3600 * 1000;
        jdbc.update("INSERT INTO t_audit_log(audit_id, category, operation, level, module, description, success,"
                + " create_time) VALUES ('stale','LOGIN','LOGIN','INFO','auth','旧记录',1,?)", stale);
        jdbc.update("INSERT INTO t_audit_api_log(trace_id, http_method, request_path, status_code, cost_millis,"
                + " slow, error, create_time) VALUES ('stale','GET','/old',200,1,0,0,?)", stale);
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/audit/maintenance/cleanup"))
                        .header("satoken", token)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((int) JsonPath.read(response.body(), "$.data.auditLogs")).isGreaterThanOrEqualTo(1);
        assertThat((int) JsonPath.read(response.body(), "$.data.apiLogs")).isGreaterThanOrEqualTo(1);
        Integer staleLeft = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_audit_log WHERE create_time < ?", Integer.class,
                System.currentTimeMillis() - 90L * 24 * 3600 * 1000);
        assertThat(staleLeft).isZero();
    }

    @Test
    void alertEvaluatorNotifiesChannelsOverThreshold() {
        CollectingAlertChannel.received.clear();
        for (int i = 0; i < 10; i++) {
            jdbc.update("INSERT INTO t_audit_log(audit_id, category, operation, level, module, description,"
                    + " success, tenant_id, create_time) VALUES (?,'LOGIN','LOGIN','WARN','auth','用户登录失败',0,1,?)",
                    "alert-" + System.nanoTime() + "-" + i, System.currentTimeMillis());
        }
        List<AuditAlertMessage> alerts = alertService.evaluate();
        assertThat(alerts).isNotEmpty();
        assertThat(CollectingAlertChannel.received).isNotEmpty();
        assertThat(CollectingAlertChannel.received.get(0).ruleId()).isEqualTo("audit-login-failures");
    }

    @Test
    void alertEvaluatorQuietBelowThreshold() {
        CollectingAlertChannel.received.clear();
        jdbc.update("INSERT INTO t_audit_log(audit_id, category, operation, level, module, description,"
                + " success, tenant_id, create_time) VALUES ('one','LOGIN','LOGIN','WARN','auth','用户登录失败',0,1,?)",
                System.currentTimeMillis());
        List<AuditAlertMessage> alerts = alertService.evaluate();
        assertThat(alerts).isEmpty();
        assertThat(CollectingAlertChannel.received).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AlertTestConfig {

        @Bean
        public AuditAlertChannel collectingAlertChannel() {
            return new CollectingAlertChannel();
        }
    }

    static class CollectingAlertChannel implements AuditAlertChannel {

        static final List<AuditAlertMessage> received = new CopyOnWriteArrayList<>();

        @Override
        public String name() {
            return "collecting";
        }

        @Override
        public void send(AuditAlertMessage message) {
            received.add(message);
        }
    }
}
