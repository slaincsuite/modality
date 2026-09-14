package cn.slainc.session;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.enums.LoginType;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.security.StpKit;
import cn.slainc.security.config.SecurityConfigReader;
import cn.slainc.session.config.SessionConfigReader;
import cn.slainc.session.extend.SessionExtender;
import cn.slainc.session.extend.SessionRenewalService;
import cn.slainc.session.kick.KickSessionService;
import cn.slainc.session.online.OnlineUserService;
import cn.slainc.session.query.SessionQueryService;
import cn.slainc.session.stats.SessionStatsService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class SessionTestSupport {

    public static final String PASSWORD = "Session@2026";

    public static final long ADMIN_ONE = 1L;

    public static final long ADMIN_TWO = 2L;

    public static final long USER_ONE = 101L;

    public static final long USER_TWO = 102L;

    public static final long USER_THREE = 103L;

    public static final long CLIENT_ONE = 201L;

    public static final long CLIENT_TWO = 202L;

    @LocalServerPort
    protected int port;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected DynamicConfigService config;

    @Autowired
    protected SecurityConfigReader securityConfig;

    @Autowired
    protected SessionConfigReader sessionConfig;

    @Autowired
    protected SessionQueryService queryService;

    @Autowired
    protected OnlineUserService onlineService;

    @Autowired
    protected SessionStatsService statsService;

    @Autowired
    protected SessionExtender extender;

    @Autowired
    protected SessionRenewalService renewalService;

    @Autowired
    protected KickSessionService kickSessionService;

    @Autowired
    protected RecordingSessionKickNotifier notifier;

    @Autowired
    protected FakeWebSocketBroadcaster broadcaster;

    @Autowired
    protected TestEventCollector events;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void accountProperties(DynamicPropertyRegistry registry) {
        String hash = BCryptUtils.hash(PASSWORD, 4);
        Object[][] accounts = {{"admin1", ADMIN_ONE, "ADMIN"}, {"admin2", ADMIN_TWO, "ADMIN"},
                {"user1", USER_ONE, "USER"}, {"user2", USER_TWO, "USER"}, {"user3", USER_THREE, "USER"},
                {"client1", CLIENT_ONE, "CLIENT"}, {"client2", CLIENT_TWO, "CLIENT"}};
        for (int index = 0; index < accounts.length; index++) {
            Object[] account = accounts[index];
            String prefix = "modality.security.accounts[" + index + "].";
            registry.add(prefix + "username", () -> (String) account[0]);
            registry.add(prefix + "password-bcrypt", () -> hash);
            registry.add(prefix + "user-id", () -> (Long) account[1]);
            registry.add(prefix + "type", () -> (String) account[2]);
        }
    }

    @BeforeEach
    void cleanSessionState() {
        deleteByPrefix("satoken:*");
        deleteByPrefix("modality:security:*");
        deleteByPrefix("modality:session:*");
        events.clear();
        notifier.clear();
        broadcaster.clear();
        resetHotConfig();
    }

    @AfterEach
    void resetSessionState() {
        resetHotConfig();
    }

    protected String loginDirect(LoginType type, long loginId) {
        return loginDirect(type, loginId, "default", null, "127.0.0.1", null, null);
    }

    protected String loginDirect(LoginType type, long loginId, String deviceType) {
        return loginDirect(type, loginId, deviceType, null, "127.0.0.1", null, null);
    }

    protected String loginDirect(LoginType type, long loginId, String deviceType, String deviceId) {
        return loginDirect(type, loginId, deviceType, deviceId, "127.0.0.1", null, null);
    }

    // 无 HTTP 上下文的直连建会话：走 createLoginSession，跳过写 Cookie / 响应头（那两步依赖 Servlet 上下文）
    protected String loginDirect(LoginType type, long loginId, String deviceType, String deviceId, String ip,
                                 String userAgent, String tenantId) {
        SaLoginParameter parameter = new SaLoginParameter();
        parameter.setDeviceType(deviceType == null ? "default" : deviceType);
        parameter.setDeviceId(deviceId);
        parameter.setTerminalExtra("ip", ip == null ? "127.0.0.1" : ip);
        parameter.setTerminalExtra("ua", userAgent == null ? "JUnit-Test-Agent" : userAgent);
        parameter.setTerminalExtra("loginTime", System.currentTimeMillis());
        parameter.setTerminalExtra("deviceId", deviceId == null ? "" : deviceId);
        parameter.setTerminalExtra("username", "直连账号" + loginId);
        if (tenantId != null) {
            parameter.setTerminalExtra("tenantId", tenantId);
        }
        parameter.setTimeout(securityConfig.sessionTimeout(type));
        parameter.setIsConcurrent(true);
        parameter.setIsShare(false);
        parameter.setMaxLoginCount(-1);
        return StpKit.of(type).createLoginSession(loginId, parameter);
    }

    protected void deleteByPrefix(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    protected void deleteKey(String key) {
        redis.delete(key);
    }

    protected void setLastActive(String type, String tokenValue, long epochMillis) {
        redis.opsForValue().set("satoken:" + type + ":last-active:" + tokenValue, String.valueOf(epochMillis));
    }

    protected void deleteLastActive(String type, String tokenValue) {
        redis.delete("satoken:" + type + ":last-active:" + tokenValue);
    }

    protected Map<String, String> loginHeaders(LoginType type) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Login-Type", type.value());
        return headers;
    }

    protected HttpResponse<String> loginHttp(LoginType type, String username, String deviceType, String deviceId,
                                             String clientIp) {
        String body = """
                {"username":"%s","password":"%s","deviceType":"%s","deviceId":"%s","clientIp":"%s"}
                """.formatted(username, PASSWORD, deviceType == null ? "" : deviceType,
                deviceId == null ? "" : deviceId, clientIp == null ? "" : clientIp);
        return post("/auth/login", type, null, body);
    }

    protected String tokenOf(LoginType type, String username) {
        HttpResponse<String> response = loginHttp(type, username, null, null, null);
        if (response.statusCode() != 200) {
            throw new IllegalStateException("登录失败 HTTP " + response.statusCode() + "：" + response.body());
        }
        return (String) JsonPath.parse(response.body()).read("$.data.tokenValue");
    }

    protected String tokenOf(LoginType type, String username, String deviceType, String deviceId) {
        HttpResponse<String> response = loginHttp(type, username, deviceType, deviceId, null);
        return (String) JsonPath.parse(response.body()).read("$.data.tokenValue");
    }

    protected HttpResponse<String> get(String path, LoginType type, String token) {
        return exchange(path, "GET", type, token, null);
    }

    protected HttpResponse<String> post(String path, LoginType type, String token, String body) {
        return exchange(path, "POST", type, token, body);
    }

    protected HttpResponse<String> exchange(String path, String method, LoginType type, String token, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (type != null) {
            builder.header("X-Login-Type", type.value());
        }
        if (token != null) {
            builder.header("satoken", token);
        }
        builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException | java.lang.InterruptedException e) {
            throw new IllegalStateException("HTTP 请求异常：" + path, e);
        }
    }

    protected Integer codeOf(HttpResponse<String> response) {
        return JsonPath.parse(response.body()).read("$.code");
    }

    protected String messageOf(HttpResponse<String> response) {
        return JsonPath.parse(response.body()).read("$.message");
    }

    protected <T> T dataOf(HttpResponse<String> response, String path) {
        String jsonPath = path == null || path.isBlank() ? "$.data" : "$.data." + path;
        return JsonPath.parse(response.body()).read(jsonPath);
    }

    private void resetHotConfig() {
        String[] keys = {"modality.session.ttl-seconds", "modality.session.display.mask-token",
                "modality.session.max-sessions-per-user", "modality.session.max-scan",
                "modality.session.idle-timeout-seconds", "modality.session.max-lifetime-seconds",
                "modality.session.cleanup.interval-minutes", "modality.session.cleanup.zombie-idle-seconds"};
        for (String key : keys) {
            try {
                config.remove(key);
            } catch (Exception ignored) {
            }
        }
    }
}
