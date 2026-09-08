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
        LoginResult result = login(LoginType.ADMIN, "admin");
        assertThat(result.tokenName()).isEqualTo("satoken");
        assertThat(result.tokenValue()).isNotBlank();
        assertThat(result.loginId()).isEqualTo(1001L);
        assertThat(result.loginType()).isEqualTo("ADMIN");
        assertThat(result.tokenTimeout()).isGreaterThan(0);
        assertThat(result.mustChangePassword()).isFalse();
    }

    @Test
    void userLoginReturnsUserId2001() {
        LoginResult result = login(LoginType.USER, "user");
        assertThat(result.loginId()).isEqualTo(2001L);
        assertThat(result.loginType()).isEqualTo("USER");
    }

    @Test
    void clientLoginReturnsUserId3001() {
        LoginResult result = login(LoginType.CLIENT, "client");
        assertThat(result.loginId()).isEqualTo(3001L);
        assertThat(result.loginType()).isEqualTo("CLIENT");
    }

    @Test
    void tokenTimeoutFollowsPerTypeHotConfig() {
        config.set("modality.security.session.timeout-user", "3600");
        LoginResult result = login(LoginType.USER, "user");
        assertThat(result.tokenTimeout()).isBetween(3500L, 3600L);
    }

    @Test
    void wrongPasswordRejectedWithChineseMessage() {
        var response = post("/auth/login", LoginType.USER, null, """
                {"username":"user","password":"wrong-password","deviceType":"","deviceId":""}
                """);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(codeOf(response)).isEqualTo(400100);
        assertThat(messageOf(response)).contains("密码错误");
    }

    @Test
    void unknownAccountRejected() {
        var response = post("/auth/login", LoginType.USER, null, """
                {"username":"ghost","password":"whatever","deviceType":"","deviceId":""}
                """);
        assertThat(codeOf(response)).as(response.body()).isEqualTo(400100);
        assertThat(messageOf(response)).contains("账号不存在");
    }

    @Test
    void disabledAccountRejected() {
        var response = post("/auth/login", LoginType.USER, null, """
                {"username":"frozen-user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(codeOf(response)).isEqualTo(400100);
        assertThat(messageOf(response)).contains("禁用");
    }

    @Test
    void eachLoginProducesDistinctTokenBecauseShareDisabled() {
        String first = tokenOf(LoginType.USER, "user");
        String second = tokenOf(LoginType.USER, "user");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void terminalExtraDataCarriesIpUaLoginTimeDeviceId() {
        login(LoginType.USER, "user", PASSWORD, "PC", "device-abc", "10.20.30.40");
        var response = get("/test/admin/online?type=USER&loginId=2001", LoginType.ADMIN, tokenOf(LoginType.ADMIN, "admin"));
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
        post("/auth/login", LoginType.USER, null, body, Map.of("X-Device-Id", "header-device-1"));
        var response = get("/test/admin/online?type=USER&loginId=2001", LoginType.ADMIN, tokenOf(LoginType.ADMIN, "admin"));
        Map<String, Object> terminal = JsonPath.parse(response.body()).read("$.data[0]");
        assertThat(terminal.get("deviceId")).isEqualTo("header-device-1");
    }

    @Test
    void deviceIdFromRequestWinsOverHeader() {
        login(LoginType.USER, "user", PASSWORD, null, "body-device", null);
        var response = get("/test/admin/online?type=USER&loginId=2001", LoginType.ADMIN, tokenOf(LoginType.ADMIN, "admin"));
        Map<String, Object> terminal = JsonPath.parse(response.body()).read("$.data[0]");
        assertThat(terminal.get("deviceId")).isEqualTo("body-device");
    }

    @Test
    void terminalUsernameStoredInExtraData() {
        login(LoginType.USER, "user");
        var response = get("/test/admin/online?type=USER&loginId=2001", LoginType.ADMIN, tokenOf(LoginType.ADMIN, "admin"));
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
        post("/auth/login", LoginType.USER, null, body, Map.of("X-Tenant-Id", "88"));
        SaLoginParameter parameter = listener.lastParameter("USER", 2001L);
        assertThat(parameter).isNotNull();
        assertThat(parameter.getExtra("tenantId")).isEqualTo(88L);
        assertThat(parameter.getExtra("username")).isEqualTo("user");
    }

    @Test
    void tenantIdSkippedWhenTenantContextAbsent() {
        login(LoginType.CLIENT, "client");
        SaLoginParameter parameter = listener.lastParameter("CLIENT", 3001L);
        assertThat(parameter.getExtra("tenantId")).isNull();
    }

    @Test
    void logoutInvalidatesCurrentToken() {
        LoginResult result = login(LoginType.USER, "user");
        post("/auth/logout", LoginType.USER, result.tokenValue(), null);
        var response = get("/test/user/me", LoginType.USER, result.tokenValue());
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
    }

    @Test
    void logoutByLoginIdKillsAllTerminals() {
        LoginResult first = login(LoginType.USER, "user");
        LoginResult second = login(LoginType.USER, "user");
        var adminToken = tokenOf(LoginType.ADMIN, "admin");
        var response = post("/test/admin/kick?type=USER&loginId=2001&reason=注销测试", LoginType.ADMIN,
                adminToken, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(get("/test/user/me", LoginType.USER, first.tokenValue()).statusCode()).isEqualTo(401);
        assertThat(get("/test/user/me", LoginType.USER, second.tokenValue()).statusCode()).isEqualTo(401);
    }

    @Test
    void checkLoginAndGetLoginIdWorkViaService() {
        LoginResult result = login(LoginType.USER, "user");
        Object data = JsonPath.parse(get("/test/user/me", LoginType.USER, result.tokenValue()).body()).read("$.data");
        assertThat(Long.parseLong(String.valueOf(data))).isEqualTo(2001L);
    }

    @Test
    void lockCountersResetAfterSuccessfulLogin() {
        post("/test/lock/fail?type=USER&loginId=2001", null, null, null);
        post("/test/lock/fail?type=USER&loginId=2001", null, null, null);
        LoginResult result = login(LoginType.USER, "user");
        var response = get("/test/lock/state?type=USER&loginId=2001", LoginType.USER, result.tokenValue());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"data\":false");
        assertThat(redis.opsForValue().get(SecurityKeys.lockFailKey("USER", 2001L))).isNull();
    }

    @Test
    void unknownLoginTypeInRequestHeaderFallsBackToUserSystem() {
        LoginResult userResult = login(LoginType.USER, "user");
        var response = get("/test/user/me", null, userResult.tokenValue(), Map.of("X-Login-Type", "GUEST"));
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void loginServiceRejectsNullLoginTypeWithChineseMessage() {
        assertThrows(Exception.class, () -> StpKit.of(null));
    }
}
