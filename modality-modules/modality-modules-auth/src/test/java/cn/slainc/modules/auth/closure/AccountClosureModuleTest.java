package cn.slainc.modules.auth.closure;
import cn.slainc.modules.auth.service.AccountClosureService;

import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.auth.AuthModuleApplication;
import cn.slainc.modules.upms.entity.UserRecord;
import cn.slainc.modules.upms.service.UserService;
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
                "modality.auth.closure.cleanup-interval-seconds=3600"})
class AccountClosureModuleTest {

    private static final String PASSWORD = "Password#123";

    @Autowired
    private UserService userService;

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
        jdbc.execute("DELETE FROM t_user");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void closureAppliesCancelsAndExecutesAfterCoolDown() throws Exception {
        UserRecord user = createUser("leaver");
        String token = login("leaver");

        HttpResponse<String> wrongPassword = post("/auth/account/close",
                "{\"password\":\"Wrong#123\",\"reason\":\"不再使用\"}", token);
        assertThat(wrongPassword.statusCode()).as(wrongPassword.body()).isEqualTo(400);
        assertThat(wrongPassword.body()).contains("密码错误");

        HttpResponse<String> apply = post("/auth/account/close",
                "{\"password\":\"" + PASSWORD + "\",\"reason\":\"不再使用\"}", token);
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
                "{\"password\":\"" + PASSWORD + "\",\"reason\":\"想清楚了\"}", token);
        assertThat(reapply.statusCode()).as(reapply.body()).isEqualTo(200);

        // 模拟冷静期结束：把到期时间拨到过去
        jdbc.update("UPDATE t_auth_account_closure SET cool_down_until = ? WHERE user_id = ? AND status = 'PENDING'",
                System.currentTimeMillis() - 1000, user.getId());

        int executed = closureService.executeDue();
        assertThat(executed).isEqualTo(1);
        Integer deleted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE id = ? AND deleted = 1", Integer.class, user.getId());
        assertThat(deleted).isEqualTo(1);
        String recordStatus = jdbc.queryForObject(
                "SELECT status FROM t_auth_account_closure WHERE user_id = ? ORDER BY id DESC LIMIT 1", String.class, user.getId());
        assertThat(recordStatus).isEqualTo("EXECUTED");
    }

    private UserRecord createUser(String username) {
        return userService.create(username, username, PASSWORD, null, null, null, LoginType.TENANT_USER, 1L,
                "module-test");
    }

    private String login(String username) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null);
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
