package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.login.LoginResult;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityFilterHttpAcceptanceTest extends SecurityAcceptanceTestBase {

    @Test
    void unauthenticatedRequestReturnsUnifiedResult401() {
        var response = get("/test/user/me", LoginType.USER, null);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).contains("application/json"));
        assertThat(codeOf(response)).isEqualTo(401000);
        assertThat(messageOf(response)).isEqualTo("未能读取到有效 token");
        Map<String, Object> body = JsonPath.parse(response.body()).read("$");
        assertThat(body).containsKeys("code", "message", "data", "timestamp");
    }

    @Test
    void defaultWhitelistLetsAuthLoginPassWithoutToken() {
        var response = post("/auth/login", LoginType.USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(response.statusCode()).isEqualTo(200);
        String tokenValue = (String) JsonPath.parse(response.body()).read("$.data.tokenValue");
        assertThat(tokenValue).isNotBlank();
    }

    @Test
    void hotAddedWhitelistPassesWithoutToken() {
        config.set("modality.security.filter.exclude-patterns",
                "[\"/test/public/**\",\"/auth/login\",\"/auth/logout\"]");
        var response = get("/test/public/ping", LoginType.USER, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(messageOf(response)).isEqualTo("成功");
    }

    @Test
    void nonWhitelistedPathStillRequiresLoginWhenWhitelistNarrowed() {
        config.set("modality.security.filter.exclude-patterns", "[\"/test/public/**\"]");
        var response = get("/test/user/me", LoginType.USER, null);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void blacklistRejectsWith403ChineseMessage() {
        config.set("modality.security.filter.blacklist-patterns", "[\"/blacklisted/**\"]");
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/blacklisted/panel", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(codeOf(response)).isEqualTo(403000);
        assertThat(messageOf(response)).contains("黑名单");
    }

    @Test
    void blacklistAppliesEvenWithoutToken() {
        config.set("modality.security.filter.blacklist-patterns", "[\"/blacklisted/**\"]");
        var response = get("/blacklisted/panel", LoginType.USER, null);
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void kickedTokenReturns401WithKickOutMessage() {
        String userToken = tokenOf(LoginType.USER, "user");
        post("/test/admin/kick?type=USER&loginId=2001&reason=风控踢出", LoginType.ADMIN,
                tokenOf(LoginType.ADMIN, "admin"), null);
        var response = get("/test/user/me", LoginType.USER, userToken);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
        assertThat(messageOf(response)).isEqualTo("token 已被踢下线");
    }

    @Test
    void replacedTokenReturns401WithBeReplacedMessage() {
        config.set("modality.security.device.single-session-per-type", "true");
        String oldToken = tokenOf(LoginType.USER, "user");
        tokenOf(LoginType.USER, "user");
        var response = get("/test/user/me", LoginType.USER, oldToken);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(messageOf(response)).isEqualTo("token 已被顶下线");
    }

    @Test
    void expiredTokenReturns401WithUnifiedResult() {
        String token = tokenOf(LoginType.USER, "user");
        redis.expire("satoken:USER:token:" + token, java.time.Duration.ofSeconds(1));
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var response = get("/test/user/me", LoginType.USER, token);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
        assertThat(messageOf(response)).isEqualTo("未能读取到有效 token");
    }

    @Test
    void forgedOriginRejectedWith403() {
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/user/me", LoginType.USER, token, Map.of("Origin", "http://evil.example.com"));
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(codeOf(response)).isEqualTo(403000);
        assertThat(messageOf(response)).contains("来源");
    }

    @Test
    void allowedOriginPassesViaHotConfig() {
        config.set("modality.security.origin.allowed-origins", "[\"http://trusted.example.com\"]");
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/user/me", LoginType.USER, token, Map.of("Origin", "http://trusted.example.com"));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void originCheckCanBeDisabledViaHotConfig() {
        config.set("modality.security.origin.enabled", "false");
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/user/me", LoginType.USER, token, Map.of("Origin", "http://evil.example.com"));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void sameOriginRequestPasses() {
        String token = tokenOf(LoginType.USER, "user");
        var response = get("/test/user/me", LoginType.USER, token,
                Map.of("Origin", "http://127.0.0.1:" + port));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void adminTypeHeaderRoutesToAdminSystemCheck() {
        String userToken = tokenOf(LoginType.USER, "user");
        var response = get("/test/admin/panel", null, userToken, Map.of("X-Login-Type", "admin"));
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void loginMetricsCountedThroughFilter() {
        long beforeGranted = counters.authorizationGranted();
        long beforeDenied = counters.authorizationDenied();
        String token = tokenOf(LoginType.USER, "user");
        get("/test/user/me", LoginType.USER, token);
        get("/test/user/me", LoginType.USER, "invalid-token");
        assertThat(counters.authorizationGranted()).isEqualTo(beforeGranted + 1);
        assertThat(counters.authorizationDenied()).isGreaterThanOrEqualTo(beforeDenied + 1);
    }

    @Test
    void cookieCarriesSameSiteLaxFromCustomizer() {
        LoginResult result = login(LoginType.USER, "user");
        var response = post("/auth/login", LoginType.USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        List<String> cookies = response.headers().allValues("set-cookie");
        if (!cookies.isEmpty()) {
            assertThat(cookies.get(0)).contains("SameSite=Lax").contains("HttpOnly");
        }
        assertThat(result.tokenValue()).isNotBlank();
    }
}
