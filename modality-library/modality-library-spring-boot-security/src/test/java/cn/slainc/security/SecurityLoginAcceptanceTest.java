package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.login.LoginResult;
import cn.slainc.security.config.SecurityKeys;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityLoginAcceptanceTest extends SecurityAcceptanceTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    TestSaTokenListener listener;

    @Test
    void adminLoginReturnsFullResult() {
        LoginResult result = login(LoginType.PLATFORM_ADMIN, "admin");
        assertThat(result.tokenName()).isEqualTo("satoken");
        assertThat(result.tokenValue()).isNotBlank();
        assertThat(result.loginId()).isEqualTo(1001L);
        assertThat(result.loginType()).isEqualTo("PLATFORM_ADMIN");
        assertThat(result.tokenTimeout()).isGreaterThan(0);
        assertThat(result.mustChangePassword()).isFalse();
    }

    @Test
    void userLoginReturnsUserId2001() {
        LoginResult result = login(LoginType.TENANT_USER, "user");
        assertThat(result.loginId()).isEqualTo(2001L);
        assertThat(result.loginType()).isEqualTo("TENANT_USER");
    }

    @Test
    void clientLoginReturnsUserId3001() {
        LoginResult result = login(LoginType.APP, "client");
        assertThat(result.loginId()).isEqualTo(3001L);
        assertThat(result.loginType()).isEqualTo("APP");
    }

    @Test
    void tokenTimeoutFollowsPerTypeHotConfig() {
        config.set("modality.security.session.timeout-tenant-user", "3600");
        LoginResult result = login(LoginType.TENANT_USER, "user");
        assertThat(result.tokenTimeout()).isBetween(3500L, 3600L);
    }

    @Test
    void wrongPasswordRejectedWithChineseMessage() {
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"wrong-password","deviceType":"","deviceId":""}
                """);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(codeOf(response)).isEqualTo(400100);
        assertThat(messageOf(response)).contains("密码错误");
    }

    @Test
    void unknownAccountRejected() {
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"ghost","password":"whatever","deviceType":"","deviceId":""}
                """);
        assertThat(codeOf(response)).as(response.body()).isEqualTo(400100);
        assertThat(messageOf(response)).contains("账号不存在");
    }

    @Test
    void disabledAccountRejected() {
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"frozen-user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(codeOf(response)).isEqualTo(400100);
        assertThat(messageOf(response)).contains("禁用");
    }

    @Test
    void eachLoginProducesDistinctTokenBecauseShareDisabled() {
        String first = tokenOf(LoginType.TENANT_USER, "user");
        String second = tokenOf(LoginType.TENANT_USER, "user");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void terminalExtraDataCarriesIpUaLoginTimeDeviceId() {
        login(LoginType.TENANT_USER, "user", PASSWORD, "PC", "device-abc", "10.20.30.40");
        var response = get("/test/admin/online?type=TENANT_USER&loginId=2001", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"));
        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> terminal = JsonPath.parse(response.body()).read("$.data[0]");
        assertThat(terminal.get("deviceId")).isEqualTo("device-abc");
        assertThat(terminal.get("deviceType")).isEqualTo("PC");
        @SuppressWarnings("unchecked")
        Map<String, Object> extraData = (Map<String, Object>) terminal.get("extraData");
        assertThat(extraData.get("ip")).isEqualTo("10.20.30.40");
        assertThat(String.valueOf(extraData.get("ua"))).containsIgnoringCase("java");
        assertThat(Long.parseLong(String.valueOf(extraData.get("loginTime")))).isPositive();
        assertThat(String.valueOf(extraData.get("deviceId"))).isEqualTo("device-abc");
    }

    @Test
    void deviceIdFallsBackToConfiguredHeader() {
        String body = """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":"","clientIp":""}
                """;
        post("/auth/login", LoginType.TENANT_USER, null, body, Map.of("X-Device-Id", "header-device-1"));
        var response = get("/test/admin/online?type=TENANT_USER&loginId=2001", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"));
        Map<String, Object> terminal = JsonPath.parse(response.body()).read("$.data[0]");
        assertThat(terminal.get("deviceId")).isEqualTo("header-device-1");
    }

    @Test
    void deviceIdFromRequestWinsOverHeader() {
        login(LoginType.TENANT_USER, "user", PASSWORD, null, "body-device", null);
        var response = get("/test/admin/online?type=TENANT_USER&loginId=2001", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"));
        Map<String, Object> terminal = JsonPath.parse(response.body()).read("$.data[0]");
        assertThat(terminal.get("deviceId")).isEqualTo("body-device");
    }

    @Test
    void terminalUsernameStoredInExtraData() {
        login(LoginType.TENANT_USER, "user");
        var response = get("/test/admin/online?type=TENANT_USER&loginId=2001", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"));
        Map<String, Object> terminal = JsonPath.parse(response.body()).read("$.data[0]");
        @SuppressWarnings("unchecked")
        Map<String, Object> extraData = (Map<String, Object>) terminal.get("extraData");
        assertThat(extraData.get("username")).isEqualTo("user");
    }

    @Test
    void tenantIdStoredWhenTenantContextPresent() {
        String body = """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":"","clientIp":""}
                """;
        post("/auth/login", LoginType.TENANT_USER, null, body, Map.of("X-Tenant-Id", "88"));
        SaLoginParameter parameter = listener.lastParameter("TENANT_USER", 2001L);
        assertThat(parameter).isNotNull();
        assertThat(parameter.getExtra("tenantId")).isEqualTo(88L);
        assertThat(parameter.getExtra("username")).isEqualTo("user");
    }

    @Test
    void tenantIdSkippedWhenTenantContextAbsent() {
        login(LoginType.APP, "client");
        SaLoginParameter parameter = listener.lastParameter("APP", 3001L);
        assertThat(parameter.getExtra("tenantId")).isNull();
    }

    @Test
    void logoutInvalidatesCurrentToken() {
        LoginResult result = login(LoginType.TENANT_USER, "user");
        post("/auth/logout", LoginType.TENANT_USER, result.tokenValue(), null);
        var response = get("/test/user/me", LoginType.TENANT_USER, result.tokenValue());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
    }

    @Test
    void logoutByLoginIdKillsAllTerminals() {
        LoginResult first = login(LoginType.TENANT_USER, "user");
        LoginResult second = login(LoginType.TENANT_USER, "user");
        var adminToken = tokenOf(LoginType.PLATFORM_ADMIN, "admin");
        var response = post("/test/admin/kick?type=TENANT_USER&loginId=2001&reason=注销测试", LoginType.PLATFORM_ADMIN,
                adminToken, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, first.tokenValue()).statusCode()).isEqualTo(401);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, second.tokenValue()).statusCode()).isEqualTo(401);
    }

    @Test
    void checkLoginAndGetLoginIdWorkViaService() {
        LoginResult result = login(LoginType.TENANT_USER, "user");
        Object data = JsonPath.parse(get("/test/user/me", LoginType.TENANT_USER, result.tokenValue()).body()).read("$.data");
        assertThat(Long.parseLong(String.valueOf(data))).isEqualTo(2001L);
    }

    @Test
    void lockCountersResetAfterSuccessfulLogin() {
        post("/test/lock/fail?type=TENANT_USER&loginId=2001", null, null, null);
        post("/test/lock/fail?type=TENANT_USER&loginId=2001", null, null, null);
        LoginResult result = login(LoginType.TENANT_USER, "user");
        var response = get("/test/lock/state?type=TENANT_USER&loginId=2001", LoginType.TENANT_USER, result.tokenValue());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"data\":false");
        assertThat(redis.opsForValue().get(SecurityKeys.lockFailKey("TENANT_USER", 2001L))).isNull();
    }

    @Test
    void unknownLoginTypeInRequestHeaderFallsBackToUserSystem() {
        LoginResult userResult = login(LoginType.TENANT_USER, "user");
        var response = get("/test/user/me", null, userResult.tokenValue(), Map.of("X-Login-Type", "bogus"));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void loginServiceRejectsNullLoginTypeWithChineseMessage() {
        assertThrows(Exception.class, () -> StpKit.of(null));
    }
}
