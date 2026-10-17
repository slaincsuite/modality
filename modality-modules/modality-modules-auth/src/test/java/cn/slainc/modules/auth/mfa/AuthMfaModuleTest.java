package cn.slainc.modules.auth.mfa;

import cn.slainc.core.context.TenantContext;
import cn.slainc.mfa.config.MfaConfigReader;
import cn.slainc.mfa.config.MfaProperties;
import cn.slainc.mfa.totp.TotpEngine;
import cn.slainc.modules.auth.AuthModuleApplication;
import cn.slainc.modules.auth.TestAccountRegistry;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

// 端到端覆盖：绑定激活 -> 登录改走二次验证 -> 挑战码换登录态 -> 恢复码 -> 关闭；需要本机 Redis
@SpringBootTest(classes = AuthModuleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthMfaModuleTest {

    @Autowired
    private TestAccountRegistry registry;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

    private HttpClient http;

    private final TotpEngine totp = new TotpEngine(new MfaConfigReader(new MfaProperties(), null));

    // 模块测试里密钥经 MfaSecretCodec 加密落库，明文只留在测试侧按用户名索引
    private final Map<String, String> secrets = new ConcurrentHashMap<>();

    @BeforeAll
    static void requireRedis() {
        LocalRedis.assumeRedisAvailable();
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        for (String prefix : new String[]{"satoken:", "modality:security:", "modality:auth:", "modality:mfa:"}) {
            var keys = redis.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        jdbc.execute("DELETE FROM t_mfa_factor");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Test
    void enrollActivateThenLoginRequiresSecondFactor() throws Exception {
        registry.create("mfaalice");
        String plainToken = loginPlain("mfaalice");
        assertThat(plainToken).isNotBlank();

        HttpResponse<String> statusBefore = get("/auth/mfa/status", plainToken);
        assertThat(statusBefore.statusCode()).as(statusBefore.body()).isEqualTo(200);
        assertThat(statusBefore.body()).contains("\"enrolled\":false");

        HttpResponse<String> enroll = post("/auth/mfa/enroll", "{}", plainToken);
        assertThat(enroll.statusCode()).as(enroll.body()).isEqualTo(200);
        String secret = JsonPath.<String>read(enroll.body(), "$.data.secret");
        assertThat(secret).hasSize(32);
        assertThat((String) JsonPath.read(enroll.body(), "$.data.otpauthUrl"))
                .startsWith("otpauth://totp/modality:mfaalice?secret=");

        HttpResponse<String> activate = post("/auth/mfa/activate",
                "{\"code\":\"" + totp.generate(secret) + "\"}", plainToken);
        assertThat(activate.statusCode()).as(activate.body()).isEqualTo(200);

        assertThat(get("/auth/mfa/status", plainToken).body()).contains("\"activated\":true");

        HttpResponse<String> challengeResponse = post("/auth/login",
                "{\"username\":\"mfaalice\",\"password\":\"" + TestAccountRegistry.PASSWORD
                        + "\",\"deviceType\":\"PC\"}", null);
        assertThat(challengeResponse.statusCode()).as(challengeResponse.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(challengeResponse.body(), "$.data.secondFactorRequired"))
                .isTrue();
        assertThat((String) JsonPath.<String>read(challengeResponse.body(), "$.data.tokenValue")).isNull();
        String challengeId = JsonPath.<String>read(challengeResponse.body(), "$.data.challengeId");
        assertThat(challengeId).isNotBlank();

        HttpResponse<String> verified = post("/auth/mfa/verify",
                "{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + totp.generate(secret) + "\"}", null);
        assertThat(verified.statusCode()).as(verified.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(verified.body(), "$.data.secondFactorRequired")).isFalse();
        String token = JsonPath.<String>read(verified.body(), "$.data.tokenValue");
        assertThat(token).isNotBlank();
        assertThat(get("/auth/test/me", token).statusCode()).as("换得的令牌可访问受保护接口").isEqualTo(200);

        HttpResponse<String> replay = post("/auth/mfa/verify",
                "{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + totp.generate(secret) + "\"}", null);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(401);
        assertThat(replay.body()).contains("已失效");
    }

    @Test
    void wrongPasswordIsRejectedBeforeIssuingTheChallenge() throws Exception {
        registry.create("mfabob");
        String token = enrollAndActivate("mfabob");
        assertThat(token).isNotBlank();

        HttpResponse<String> wrong = post("/auth/login",
                "{\"username\":\"mfabob\",\"password\":\"Wrong#123\"}", null);
        assertThat(wrong.statusCode()).as(wrong.body()).isEqualTo(400);
        assertThat(wrong.body()).contains("账号或密码错误");

        String challengeId = loginForChallenge("mfabob");
        HttpResponse<String> wrongCode = post("/auth/mfa/verify",
                "{\"challengeId\":\"" + challengeId + "\",\"code\":\"000000\"}", null);
        assertThat(wrongCode.statusCode()).as(wrongCode.body()).isEqualTo(401);
        assertThat(wrongCode.body()).contains("动态码或恢复码错误");
    }

    @Test
    void recoveryCodeCompletesLoginAndCanBeRegenerated() throws Exception {
        registry.create("mfacarol");
        String token = enrollAndActivate("mfacarol");

        HttpResponse<String> regenerated = post("/auth/mfa/recovery-codes",
                "{\"code\":\"" + currentCode("mfacarol") + "\"}", token);
        assertThat(regenerated.statusCode()).as(regenerated.body()).isEqualTo(200);
        List<String> codes = JsonPath.read(regenerated.body(), "$.data");
        assertThat(codes).hasSize(10);

        String challengeId = loginForChallenge("mfacarol");
        HttpResponse<String> verified = post("/auth/mfa/verify",
                "{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + codes.get(0) + "\"}", null);
        assertThat(verified.statusCode()).as(verified.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(verified.body(), "$.data.tokenValue")).isNotBlank();

        String stored = jdbc.queryForObject(
                "SELECT recovery_codes FROM t_mfa_factor WHERE username = ?", String.class, "mfacarol");
        assertThat(stored.split(",")).hasSize(9);
    }

    @Test
    void disableStopsRequiringTheSecondFactor() throws Exception {
        registry.create("mfadave");
        String token = enrollAndActivate("mfadave");

        HttpResponse<String> wrongDisable = post("/auth/mfa/disable", "{\"code\":\"000000\"}", token);
        assertThat(wrongDisable.statusCode()).as(wrongDisable.body()).isEqualTo(400);
        assertThat(wrongDisable.body()).contains("动态码错误");

        HttpResponse<String> disable = post("/auth/mfa/disable",
                "{\"code\":\"" + currentCode("mfadave") + "\"}", token);
        assertThat(disable.statusCode()).as(disable.body()).isEqualTo(200);
        assertThat(get("/auth/mfa/status", token).body()).contains("\"enrolled\":false");

        HttpResponse<String> plain = post("/auth/login",
                "{\"username\":\"mfadave\",\"password\":\"" + TestAccountRegistry.PASSWORD + "\"}", null);
        assertThat((Boolean) JsonPath.<Boolean>read(plain.body(), "$.data.secondFactorRequired")).isFalse();
        assertThat(JsonPath.<String>read(plain.body(), "$.data.tokenValue")).isNotBlank();
    }

    @Test
    void selfServiceEndpointsRequireLogin() throws Exception {
        registry.create("mfaerin");

        HttpResponse<String> anonymous = get("/auth/mfa/status", null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
    }

    private String enrollAndActivate(String username) throws Exception {
        String token = loginPlain(username);
        HttpResponse<String> enroll = post("/auth/mfa/enroll", "{}", token);
        assertThat(enroll.statusCode()).as(enroll.body()).isEqualTo(200);
        String secret = JsonPath.read(enroll.body(), "$.data.secret");
        secrets.put(username, secret);
        HttpResponse<String> activate = post("/auth/mfa/activate",
                "{\"code\":\"" + totp.generate(secret) + "\"}", token);
        assertThat(activate.statusCode()).as(activate.body()).isEqualTo(200);
        return token;
    }

    private String loginPlain(String username) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + TestAccountRegistry.PASSWORD + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat((Boolean) JsonPath.<Boolean>read(response.body(), "$.data.secondFactorRequired")).isFalse();
        return JsonPath.read(response.body(), "$.data.tokenValue");
    }

    private String loginForChallenge(String username) throws Exception {
        HttpResponse<String> response = post("/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + TestAccountRegistry.PASSWORD + "\"}", null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        String challengeId = JsonPath.read(response.body(), "$.data.challengeId");
        assertThat(challengeId).isNotBlank();
        return challengeId;
    }

    private String currentCode(String username) {
        return totp.generate(secrets.get(username));
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
