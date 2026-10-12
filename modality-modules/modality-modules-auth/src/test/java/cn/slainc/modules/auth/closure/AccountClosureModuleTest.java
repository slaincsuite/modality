package cn.slainc.modules.auth.closure;
import cn.slainc.modules.auth.service.AccountClosureService;

import cn.slainc.core.context.TenantContext;
import cn.slainc.modules.auth.AuthModuleApplication;
import cn.slainc.modules.auth.TestAccountRegistry;
import cn.slainc.security.spi.PasswordAccount;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"modality.auth.closure.cool-down-seconds=3600",
                "modality.auth.closure.cleanup-interval-seconds=3600",
                "modality.auth.closure.max-execute-attempts=2",
                "modality.auth.closure.retry-backoff-seconds=0"})
class AccountClosureModuleTest {

    @Autowired
    private TestAccountRegistry registry;

    @Autowired
    private AccountClosureService closureService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        for (String prefix : new String[]{"satoken:", "modality:security:", "modality:auth:"}) {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        jdbc.execute("DELETE FROM t_auth_account_closure");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void closureAppliesCancelsAndExecutesAfterCoolDown() throws Exception {
        PasswordAccount account = registry.create("leaver");
        String token = login("leaver");

        HttpResponse<String> wrongPassword = post("/auth/account/close",
                "{\"password\":\"Wrong#123\",\"reason\":\"不再使用\"}", token);
        assertThat(wrongPassword.statusCode()).as(wrongPassword.body()).isEqualTo(400);
        assertThat(wrongPassword.body()).contains("密码错误");

        HttpResponse<String> apply = post("/auth/account/close",
                "{\"password\":\"" + TestAccountRegistry.PASSWORD + "\",\"reason\":\"不再使用\"}", token);
        assertThat(apply.statusCode()).as(apply.body()).isEqualTo(200);
        assertThat(apply.body()).contains("PENDING");

        HttpResponse<String> status = get("/auth/account/close/status", token);
        assertThat(status.statusCode()).as(status.body()).isEqualTo(200);
        assertThat(status.body()).contains("不再使用");

        assertThat(closureService.executeDue()).as("冷静期未结束前不执行").isZero();

        HttpResponse<String> cancel = post("/auth/account/close/cancel", "{}", token);
        assertThat(cancel.statusCode()).as(cancel.body()).isEqualTo(200);
        assertThat(get("/auth/account/close/status", token).body()).contains("CANCELLED");

        HttpResponse<String> reapply = post("/auth/account/close",
                "{\"password\":\"" + TestAccountRegistry.PASSWORD + "\",\"reason\":\"想清楚了\"}", token);
        assertThat(reapply.statusCode()).as(reapply.body()).isEqualTo(200);

        // 模拟冷静期结束：把到期时间拨到过去
        jdbc.update("UPDATE t_auth_account_closure SET cool_down_until = ? WHERE user_id = ? AND status = 'PENDING'",
                System.currentTimeMillis() - 1000, account.userId());

        int executed = closureService.executeDue();
        assertThat(executed).isEqualTo(1);
        assertThat(registry.isDeleted(account.userId())).as("注销到期后账号被软删").isTrue();
        String recordStatus = jdbc.queryForObject(
                "SELECT status FROM t_auth_account_closure WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                String.class, account.userId());
        assertThat(recordStatus).isEqualTo("EXECUTED");
    }

    @Test
    void failedExecutionKeepsPendingThenMarksFailedAfterCap() {
        PasswordAccount account = registry.create("retryer");
        closureService.apply(cn.slainc.core.enums.LoginType.TENANT_USER, account.userId(), "retryer", "重试测试");
        registry.failDelete(account.userId(), true);
        try {
            dueNow(account.userId());
            assertThat(closureService.executeDue()).as("首轮失败不计数为已执行").isZero();
            assertThat(statusOf(account.userId())).isEqualTo("PENDING");
            assertThat(attemptsOf(account.userId())).isEqualTo(1);

            dueNow(account.userId());
            assertThat(closureService.executeDue()).isZero();
            assertThat(statusOf(account.userId())).isEqualTo("FAILED");
            assertThat(attemptsOf(account.userId())).isEqualTo(2);
            assertThat(registry.isDeleted(account.userId())).isFalse();
        } finally {
            registry.failDelete(account.userId(), false);
        }
    }

    private void dueNow(long userId) {
        jdbc.update("UPDATE t_auth_account_closure SET cool_down_until = ? WHERE user_id = ? AND status = 'PENDING'",
                System.currentTimeMillis() - 1000, userId);
    }

    private String statusOf(long userId) {
        return jdbc.queryForObject(
                "SELECT status FROM t_auth_account_closure WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                String.class, userId);
    }

    private int attemptsOf(long userId) {
        Integer attempts = jdbc.queryForObject(
                "SELECT attempt_count FROM t_auth_account_closure WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Integer.class, userId);
        return attempts == null ? 0 : attempts;
    }

    private String login(String username) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + TestAccountRegistry.PASSWORD + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.<String>read(response.body(), "$.data.tokenValue");
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
