package cn.slainc.security;

import cn.slainc.core.config.DynamicConfigService;
import cn.slainc.core.enums.LoginType;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.metrics.SecurityCounters;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Map;
import java.util.Set;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class SecurityAcceptanceTestBase {

    protected static final String PASSWORD = TestAccountStore.DEFAULT_PASSWORD;

    @LocalServerPort
    protected int port;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected DynamicConfigService config;

    @Autowired
    protected TestAccountStore accounts;

    @Autowired
    protected SecurityCounters counters;

    @Autowired
    protected TestEventCollector events;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void cleanRedisState() {
        deleteByPrefix("modality:security:");
        deleteByPrefix("satoken:");
        events.clear();
        resetHotConfig();
        config.set("modality.security.filter.exclude-patterns", "[\"/auth/login\",\"/auth/logout\",\"/error\","
                + "\"/test/lock/**\",\"/test/permissions\",\"/test/roles\",\"/test/cache/**\","
                + "\"/test/password/**\",\"/test/restrict/**\",\"/test/safe/open\",\"/test/safe/state\","
                + "\"/test/safe/close\",\"/test/device/**\",\"/test/public/**\"]");
    }

    @AfterEach
    void resetHotConfigAfter() {
        resetHotConfig();
    }

    private void deleteByPrefix(String prefix) {
        Set<String> keys = redis.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private void resetHotConfig() {
        String[] keys = {"modality.security.permission-cache-ttl-seconds",
                "modality.security.lockout.max-failures", "modality.security.lockout.window-seconds",
                "modality.security.lockout.lock-minutes", "modality.security.session.timeout-admin",
                "modality.security.session.timeout-user", "modality.security.session.timeout-client",
                "modality.security.device.max-count-per-type", "modality.security.device.overflow-mode",
                "modality.security.device.single-session-per-type", "modality.security.restriction.time-start",
                "modality.security.restriction.time-end", "modality.security.restriction.ip-whitelist",
                "modality.security.restriction.ip-blacklist", "modality.security.restriction.allowed-device-types",
                "modality.security.filter.exclude-patterns", "modality.security.filter.blacklist-patterns",
                "modality.security.origin.enabled", "modality.security.origin.allowed-origins",
                "modality.security.password.min-length", "modality.security.password.require-classes",
                "modality.security.password.history-size", "modality.security.password.max-age-days"};
        for (String key : keys) {
            try {
                config.remove(key);
            } catch (Exception ignored) {
            }
        }
    }

    protected LoginResult login(LoginType type, String username) {
        return login(type, username, PASSWORD, null, null, null);
    }

    protected LoginResult login(LoginType type, String username, String password, String deviceType,
                                String deviceId, String clientIp) {
        String body = """
                {"username":"%s","password":"%s","deviceType":"%s","deviceId":"%s","clientIp":"%s"}
                """.formatted(username, password, deviceType == null ? "" : deviceType,
                deviceId == null ? "" : deviceId, clientIp == null ? "" : clientIp);
        HttpResponse<String> response = post("/auth/login", type, null, body, Map.of());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("登录失败 HTTP " + response.statusCode() + "：" + response.body());
        }
        Map<String, Object> data = JsonPath.parse(response.body()).read("$.data");
        return new LoginResult((String) data.get("tokenName"), (String) data.get("tokenValue"),
                data.get("loginId") == null ? null : Long.parseLong(String.valueOf(data.get("loginId"))),
                (String) data.get("loginType"), ((Number) data.get("tokenTimeout")).longValue(),
                Boolean.TRUE.equals(data.get("mustChangePassword")));
    }

    protected String tokenOf(LoginType type, String username) {
        return login(type, username).tokenValue();
    }

    protected String tokenOf(LoginType type, String username, String password, String deviceType, String deviceId,
                             String clientIp) {
        return login(type, username, password, deviceType, deviceId, clientIp).tokenValue();
    }

    protected HttpResponse<String> get(String path, LoginType type, String token) {
        return exchange(path, "GET", type, token, null, Map.of());
    }

    protected HttpResponse<String> get(String path, LoginType type, String token, Map<String, String> headers) {
        return exchange(path, "GET", type, token, null, headers);
    }

    protected HttpResponse<String> post(String path, LoginType type, String token, String body) {
        return exchange(path, "POST", type, token, body, Map.of());
    }

    protected HttpResponse<String> post(String path, LoginType type, String token, String body,
                                        Map<String, String> headers) {
        return exchange(path, "POST", type, token, body, headers);
    }

    protected HttpResponse<String> exchange(String path, String method, LoginType type, String token, String body,
                                            Map<String, String> headers) {
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
        headers.forEach(builder::header);
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

    protected List<String> listOf(HttpResponse<String> response, String path) {
        String jsonPath = path == null || path.isBlank() ? "$.data" : "$.data." + path;
        return JsonPath.parse(response.body()).read(jsonPath);
    }

    protected List<String> dataListOf(HttpResponse<String> response) {
        return JsonPath.parse(response.body()).read("$.data");
    }

    protected String dataOf(HttpResponse<String> response) {
        return JsonPath.parse(response.body()).read("$.data");
    }
}
