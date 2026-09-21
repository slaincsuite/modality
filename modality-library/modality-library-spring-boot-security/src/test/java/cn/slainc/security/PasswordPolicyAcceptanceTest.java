package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityKeys;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyAcceptanceTest extends SecurityAcceptanceTestBase {

    @BeforeEach
    void restoreFreshAccount() {
        accounts.register("fresh-pwd", 2005L, LoginType.TENANT_USER, true, false, null);
        redis.delete(SecurityKeys.passwordHistoryKey("TENANT_USER", 2005L));
    }

    @Test
    void mustChangePasswordFlagPassesThroughToLoginResult() {
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"must-change","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(Boolean.TRUE.equals(JsonPath.parse(response.body()).read("$.data.mustChangePassword")))
                .isTrue();
    }

    @Test
    void expiredPasswordFlagsMustChangePassword() {
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"expired-pwd","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(Boolean.TRUE.equals(JsonPath.parse(response.body()).read("$.data.mustChangePassword")))
                .isTrue();
    }

    @Test
    void freshPasswordDoesNotFlagMustChange() {
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"fresh-pwd","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(Boolean.TRUE.equals(JsonPath.parse(response.body()).read("$.data.mustChangePassword")))
                .isFalse();
    }

    @Test
    void maxAgeDaysZeroDisablesExpiry() {
        config.set("modality.security.password.max-age-days", "0");
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"expired-pwd","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(Boolean.TRUE.equals(JsonPath.parse(response.body()).read("$.data.mustChangePassword")))
                .isFalse();
    }

    @Test
    void changePasswordFullFlowViaHttp() {
        Long userId = 2005L;
        post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"Password#123","newPassword":"BrandNew#456","kickOtherSessions":false}
                """);
        var newLogin = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"fresh-pwd","password":"BrandNew#456","deviceType":"","deviceId":""}
                """);
        assertThat(newLogin.body()).contains("tokenValue");
        var oldLogin = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"fresh-pwd","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(codeOf(oldLogin)).isEqualTo(400100);
        assertThat(userId).isEqualTo(2005L);
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        var response = post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"wrong-old","newPassword":"BrandNew#456","kickOtherSessions":false}
                """);
        assertThat(codeOf(response)).isEqualTo(400100);
        assertThat(messageOf(response)).contains("旧密码不正确");
    }

    @Test
    void changePasswordRejectsWeakNewPasswordWithItemizedReason() {
        var response = post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"Password#123","newPassword":"short","kickOtherSessions":false}
                """);
        assertThat(messageOf(response)).contains("密码强度不达标").contains("长度");
    }

    @Test
    void changePasswordRejectsReusedPasswordFromHistory() {
        post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"Password#123","newPassword":"BrandNew#456","kickOtherSessions":false}
                """);
        var backToOld = post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"BrandNew#456","newPassword":"Password#123","kickOtherSessions":false}
                """);
        assertThat(codeOf(backToOld)).isEqualTo(400100);
        assertThat(messageOf(backToOld)).contains("不能与最近");
    }

    @Test
    void historyKeepsOnlyConfiguredSize() {
        config.set("modality.security.password.history-size", "2");
        post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"Password#123","newPassword":"BrandNew#456","kickOtherSessions":false}
                """);
        post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"BrandNew#456","newPassword":"Third#789Pass","kickOtherSessions":false}
                """);
        var list = redis.opsForList().range(SecurityKeys.passwordHistoryKey("TENANT_USER", 2005L), 0, -1);
        assertThat(list).hasSize(2);
    }

    @Test
    void reuseBeyondHistoryWindowAllowed() {
        config.set("modality.security.password.history-size", "1");
        post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"Password#123","newPassword":"BrandNew#456","kickOtherSessions":false}
                """);
        post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"BrandNew#456","newPassword":"Third#789Pass","kickOtherSessions":false}
                """);
        var reuse = post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"Third#789Pass","newPassword":"Password#123","kickOtherSessions":false}
                """);
        assertThat(codeOf(reuse)).isEqualTo(200);
    }

    @Test
    void changePasswordCanKickOtherSessions() {
        String token = tokenOf(LoginType.TENANT_USER, "fresh-pwd");
        post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":2005,"oldPassword":"Password#123","newPassword":"BrandNew#456","kickOtherSessions":true}
                """);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, token).statusCode()).isEqualTo(401);
    }

    @Test
    void missingAccountRejectedOnPasswordChange() {
        var response = post("/test/password/change", null, null, """
                {"type":"TENANT_USER","userId":9999,"oldPassword":"Password#123","newPassword":"BrandNew#456","kickOtherSessions":false}
                """);
        assertThat(messageOf(response)).contains("账号不存在");
    }
}
