package cn.slainc.modules.auth.login;

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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthLoginModuleTest {

    private static final String PASSWORD = "Password#123";

    @Autowired
    private UserService userService;

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
        for (String prefix : new String[]{"satoken:", "modality:security:", "modality:auth:", "modality:captcha:"}) {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        for (String table : new String[]{"t_user_login_record", "t_user_operation_log", "t_user"}) {
            jdbc.execute("DELETE FROM " + table);
        }
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private UserRecord createUser(String username) {
        return userService.create(username, username, PASSWORD, null, null, null, LoginType.TENANT_USER, 1L,
                "module-test");
    }

    @Test
    void passwordLoginIssuesTokenAndPassesProtectedEndpoint() throws Exception {
        UserRecord user = createUser("alice");
        String body = "{\"username\":\"alice\",\"password\":\"" + PASSWORD + "\",\"deviceType\":\"PC\"}";
        HttpResponse<String> response = post("/auth/login", body, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        String token = JsonPath.<String>read(response.body(), "$.data.tokenValue");
        assertThat(token).isNotBlank();
        assertThat(String.valueOf((Object) JsonPath.read(response.body(), "$.data.loginId"))).isEqualTo(String.valueOf(user.getId()));

        HttpResponse<String> me = get("/auth/test/me", token);
        assertThat(me.statusCode()).as(me.body()).isEqualTo(200);
        assertThat(me.body()).contains(String.valueOf(user.getId()));

        Integer loginRecords = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user_login_record WHERE username = 'alice' AND succeeded = 1", Integer.class);
        assertThat(loginRecords).isGreaterThanOrEqualTo(1);
    }

    @Test
    void userLevelIpRestrictionBlocksLogin() throws Exception {
        UserRecord user = createUser("erin");
        userService.update(user.getId(), null, null, null, null, null, null, null, "10.99.0.1", null, null);
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"erin\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("不在账号允许的 IP");
    }

    @Test
    void rememberMeExtendsTokenTimeout() throws Exception {
        createUser("frank");
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"frank\",\"password\":\"" + PASSWORD + "\",\"remember\":true}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(response.body(), "$.data.remember")).isTrue();
        assertThat((Integer) JsonPath.<Integer>read(response.body(), "$.data.tokenTimeout"))
                .isGreaterThanOrEqualTo(2592000);
    }

    @Test
    void singleEndLogoutKeepsOtherDeviceAndLogsOperation() throws Exception {
        UserRecord user = createUser("grace");
        String first = login("grace", "d1");
        String second = login("grace", "d2");

        HttpResponse<String> logout = post("/auth/logout", "{}", first);
        assertThat(logout.statusCode()).as(logout.body()).isEqualTo(200);
        assertThat(get("/auth/test/me", first).statusCode()).isEqualTo(401);
        assertThat(get("/auth/test/me", second).statusCode()).as("另一设备不受单端登出影响").isEqualTo(200);

        HttpResponse<String> logoutAll = post("/auth/logout/all", "{}", second);
        assertThat(logoutAll.statusCode()).as(logoutAll.body()).isEqualTo(200);
        assertThat(get("/auth/test/me", second).statusCode()).isEqualTo(401);

        Integer logoutLogs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user_operation_log WHERE module_name = 'auth' AND operation = '登出'",
                Integer.class);
        assertThat(logoutLogs).isGreaterThanOrEqualTo(2);
    }

    private String login(String username, String deviceId) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"deviceId\":\"" + deviceId
                        + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.data.tokenValue");
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
                .header("X-Login-Type", "TENANT_USER")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            builder.header("satoken", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
