package cn.slainc.modules.auth.login;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 登录链路测试以内存账号源替掉真实 upms；登录记录与操作日志的落库归 upms 模块测试覆盖
@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthLoginModuleTest {

    @Autowired
    private TestAccountRegistry registry;

    @Autowired
    private StringRedisTemplate redis;

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
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void passwordLoginIssuesTokenAndPassesProtectedEndpoint() throws Exception {
        PasswordAccount account = registry.create("alice");
        String body = "{\"username\":\"alice\",\"password\":\"" + TestAccountRegistry.PASSWORD
                + "\",\"deviceType\":\"PC\"}";
        HttpResponse<String> response = post("/auth/login", body, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        String token = JsonPath.<String>read(response.body(), "$.data.tokenValue");
        assertThat(token).isNotBlank();
        assertThat(String.valueOf((Object) JsonPath.read(response.body(), "$.data.loginId")))
                .isEqualTo(String.valueOf(account.userId()));

        HttpResponse<String> me = get("/auth/test/me", token);
        assertThat(me.statusCode()).as(me.body()).isEqualTo(200);
        assertThat(me.body()).contains(String.valueOf(account.userId()));
    }

    @Test
    void userLevelIpRestrictionBlocksLogin() throws Exception {
        registry.create("erin");
        registry.restrict("erin", List.of("当前 IP 不在账号允许的 IP 范围内"));
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"erin\",\"password\":\"" + TestAccountRegistry.PASSWORD + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("不在账号允许的 IP");
    }

    @Test
    void rememberMeExtendsTokenTimeout() throws Exception {
        registry.create("frank");
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"frank\",\"password\":\"" + TestAccountRegistry.PASSWORD
                        + "\",\"remember\":true}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(response.body(), "$.data.remember")).isTrue();
        assertThat((Integer) JsonPath.<Integer>read(response.body(), "$.data.tokenTimeout"))
                .isGreaterThanOrEqualTo(2592000);
    }

    @Test
    void singleEndLogoutKeepsOtherDevice() throws Exception {
        registry.create("grace");
        String first = login("grace", "d1");
        String second = login("grace", "d2");

        HttpResponse<String> logout = post("/auth/logout", "{}", first);
        assertThat(logout.statusCode()).as(logout.body()).isEqualTo(200);
        assertThat(get("/auth/test/me", first).statusCode()).isEqualTo(401);
        assertThat(get("/auth/test/me", second).statusCode()).as("另一设备不受单端登出影响").isEqualTo(200);

        HttpResponse<String> logoutAll = post("/auth/logout/all", "{}", second);
        assertThat(logoutAll.statusCode()).as(logoutAll.body()).isEqualTo(200);
        assertThat(get("/auth/test/me", second).statusCode()).isEqualTo(401);
    }

    private String login(String username, String deviceId) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + TestAccountRegistry.PASSWORD
                        + "\",\"deviceId\":\"" + deviceId + "\"}", null);
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
