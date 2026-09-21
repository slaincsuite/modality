package cn.slainc.boot;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.result.Result;
import cn.slainc.crypto.hash.BCryptUtils;
import cn.slainc.security.StpKit;
import cn.slainc.security.annotation.RequireLogin;
import cn.slainc.security.kick.KickService;
import cn.slainc.security.login.LoginRequest;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.login.SecurityLoginService;
import cn.slainc.security.spi.AccountAuthProvider;
import cn.slainc.security.spi.PasswordAccount;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class Batch4SecurityAcceptanceTest {

    private static final String PASSWORD = "Password#123";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;

    private HttpResponse<String> send(String method, String path, String type, String token, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (type != null) {
            builder.header("X-Login-Type", type);
        }
        if (token != null) {
            builder.header("satoken", token);
        }
        builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String login(String username, String type, String deviceType, String deviceId) throws Exception {
        var response = send("POST", "/auth/login", type, null, """
                {"username":"%s","password":"%s","deviceType":"%s","deviceId":"%s"}
                """.formatted(username, PASSWORD, deviceType, deviceId));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.parse(response.body()).read("$.data.tokenValue");
    }

    @Test
    @DisplayName("认证闭环：用户名密码登录 → Token 鉴权 → 管理员查询在线 → 踢人立即生效")
    void authenticationLoopEndsWithEffectiveKick() throws Exception {
        String userToken = login("user", "TENANT_USER", "PC", "device-1");

        var authorized = send("GET", "/batch4/me", "TENANT_USER", userToken, null);
        assertThat(authorized.statusCode()).isEqualTo(200);
        assertThat(authorized.body()).contains("2001");

        var anonymous = send("GET", "/batch4/me", "TENANT_USER", null, null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.body()).contains("未能读取到有效 token");

        String adminToken = login("admin", "PLATFORM_ADMIN", "PC", "device-admin");
        var online = send("GET", "/batch4/admin/online?type=TENANT_USER&loginId=2001", "PLATFORM_ADMIN", adminToken, null);
        assertThat(online.statusCode()).isEqualTo(200);
        assertThat(online.body()).contains("device-1");

        var kick = send("POST", "/batch4/admin/kick?type=TENANT_USER&loginId=2001&reason=违规操作", "PLATFORM_ADMIN", adminToken, null);
        assertThat(kick.statusCode()).isEqualTo(200);

        var afterKick = send("GET", "/batch4/me", "TENANT_USER", userToken, null);
        assertThat(afterKick.statusCode()).isEqualTo(401);
        assertThat(afterKick.body()).contains("token 已被踢下线");

        var adminStillAlive = send("GET", "/batch4/admin/online?type=TENANT_USER&loginId=2001", "PLATFORM_ADMIN", adminToken, null);
        assertThat(adminStillAlive.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("三套账号体系独立登录与鉴权，踢人互不影响")
    void threeAccountSystemsStayIsolated() throws Exception {
        String userToken = login("user", "TENANT_USER", "PC", "u-1");
        String clientToken = login("client", "APP", "MOBILE", "c-1");
        String adminToken = login("admin", "PLATFORM_ADMIN", "PC", "a-1");

        assertThat(userToken).isNotEqualTo(clientToken).isNotEqualTo(adminToken);
        assertThat(send("GET", "/batch4/me", "TENANT_USER", userToken, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/batch4/client/me", "APP", clientToken, null).statusCode()).isEqualTo(200);

        assertThat(send("GET", "/batch4/admin/panel", "TENANT_USER", userToken, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/batch4/admin/panel", "APP", clientToken, null).statusCode()).isEqualTo(401);

        assertThat(send("POST", "/batch4/admin/kick?type=TENANT_USER&loginId=2001", "PLATFORM_ADMIN", adminToken, null)
                .statusCode()).isEqualTo(200);
        assertThat(send("GET", "/batch4/me", "TENANT_USER", userToken, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/batch4/client/me", "APP", clientToken, null).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("JWKS 端点无需登录即可返回公钥，且不含任何私钥参数")
    void jwksEndpointServesPublicKeys(@Autowired cn.slainc.jwt.jwks.JwksService jwksService) throws Exception {
        jwksService.invalidate();
        var response = send("GET", "/oauth2/jwks", null, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"kty\"").contains("\"use\":\"sig\"").contains("\"kid\"");
        assertThat(response.body()).doesNotContain("private");
        assertThat(cn.slainc.crypto.jwt.JwkSet.parse(response.body()).size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("SaTokenDao 走的是 Redis 实现，会话数据落在 Redis 中")
    void sessionsAreStoredInRedis(@Autowired org.springframework.data.redis.core.StringRedisTemplate redis)
            throws Exception {
        String token = login("user", "TENANT_USER", "PC", "redis-check");
        assertThat(redis.opsForValue().get("satoken:TENANT_USER:token:" + token)).isEqualTo("2001");
        assertThat(StpKit.tenantUser().getTokenTimeout(token)).isGreaterThan(0);
    }
}
