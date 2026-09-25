package cn.slainc.modules.auth.login;

import cn.slainc.captcha.CaptchaChallenge;
import cn.slainc.captcha.CaptchaService;
import cn.slainc.core.context.TenantContext;
import cn.slainc.core.enums.LoginType;
import cn.slainc.modules.auth.AuthModuleApplication;
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
        properties = "modality.auth.captcha.enabled=true")
class AuthLoginCaptchaModuleTest {

    private static final String PASSWORD = "Password#123";

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaService captchaService;

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
        jdbc.execute("DELETE FROM t_user");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void loginWithoutCaptchaIsRejectedWhenCaptchaEnabled() throws Exception {
        userService.create("bob", "bob", PASSWORD, null, null, null, LoginType.TENANT_USER, 1L, "module-test");
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"bob\",\"password\":\"" + PASSWORD + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("请输入验证码");
    }

    @Test
    void loginSucceedsWithCaptchaAnswer() throws Exception {
        userService.create("carol", "carol", PASSWORD, null, null, null, LoginType.TENANT_USER, 1L, "module-test");
        CaptchaChallenge challenge = captchaService.generate("WEB", "127.0.0.1");
        String raw = redis.opsForValue().get("modality:captcha:data:WEB:" + challenge.id());
        assertThat(raw).as("验证码答案只存服务端，测试从存储侧取证").isNotNull();
        String answer = JsonPath.<String>read(raw, "$.answer");
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"carol\",\"password\":\"" + PASSWORD + "\",\"captchaId\":\"" + challenge.id()
                        + "\",\"captchaCode\":\"" + answer + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.data.tokenValue")).isNotBlank();
    }

    @Test
    void wrongCaptchaAnswerIsRejected() throws Exception {
        userService.create("dave", "dave", PASSWORD, null, null, null, LoginType.TENANT_USER, 1L, "module-test");
        CaptchaChallenge challenge = captchaService.generate("WEB", "127.0.0.1");
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"dave\",\"password\":\"" + PASSWORD + "\",\"captchaId\":\"" + challenge.id()
                        + "\",\"captchaCode\":\"0000\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("验证码校验失败");
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
