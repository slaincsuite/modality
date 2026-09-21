package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityKeys;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountSecurityAcceptanceTest extends SecurityAcceptanceTestBase {

    @Test
    void lockTriggersAfterConfiguredFailures() {
        config.set("modality.security.lockout.max-failures", "3");
        String first = tokenOf(LoginType.TENANT_USER, "user");
        for (int i = 0; i < 3; i++) {
            post("/auth/login", LoginType.TENANT_USER, null, """
                    {"username":"user","password":"bad-pass-%d","deviceType":"","deviceId":""}
                    """.formatted(i));
        }
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(codeOf(response)).isEqualTo(400100);
        assertThat(messageOf(response)).contains("锁定");
        assertThat(get("/test/lock/state?type=TENANT_USER&loginId=2001", null, null).body()).contains("true");
        assertThat(first).isNotBlank();
    }

    @Test
    void remainingAttemptsCountDownBeforeLock() {
        config.set("modality.security.lockout.max-failures", "5");
        var first = post("/test/lock/fail?type=TENANT_USER&loginId=2001", null, null, null);
        assertThat(JsonPath.parse(first.body()).<Integer>read("$.data")).isEqualTo(4);
        var second = post("/test/lock/fail?type=TENANT_USER&loginId=2001", null, null, null);
        assertThat(JsonPath.parse(second.body()).<Integer>read("$.data")).isEqualTo(3);
        assertThat(get("/test/lock/state?type=TENANT_USER&loginId=2001", null, null).body()).contains("false");
    }

    @Test
    void wrongPasswordMessageCarriesRemainingAttempts() {
        config.set("modality.security.lockout.max-failures", "5");
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"totally-wrong","deviceType":"","deviceId":""}
                """);
        assertThat(messageOf(response)).contains("剩余尝试次数 4");
    }

    @Test
    void resetUnlocksAccount() {
        config.set("modality.security.lockout.max-failures", "1");
        post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"bad","deviceType":"","deviceId":""}
                """);
        assertThat(get("/test/lock/state?type=TENANT_USER&loginId=2001", null, null).body()).contains("true");
        post("/test/lock/reset?type=TENANT_USER&loginId=2001", null, null, null);
        assertThat(get("/test/lock/state?type=TENANT_USER&loginId=2001", null, null).body()).contains("false");
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(response.body()).contains("tokenValue");
    }

    @Test
    void banBlocksLoginWithRemainHint() {
        post("/test/admin/ban?type=TENANT_USER&loginId=2001&seconds=120", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"), null);
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(codeOf(response)).isEqualTo(400100);
        assertThat(messageOf(response)).contains("封禁").contains("剩余").contains("秒");
    }

    @Test
    void unbanRestoresLogin() {
        post("/test/admin/ban?type=TENANT_USER&loginId=2001&seconds=120", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"), null);
        post("/test/admin/unban?type=TENANT_USER&loginId=2001", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"), null);
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(response.body()).contains("tokenValue");
    }

    @Test
    void banRemainReadableViaService() {
        long remain = StpKit.tenantUser().getDisableTime(2001L, "ban");
        assertThat(remain).isLessThanOrEqualTo(0);
        StpKit.tenantUser().disable(2001L, "ban", 90);
        assertThat(StpKit.tenantUser().getDisableTime(2001L, "ban")).isBetween(1L, 90L);
        assertThat(StpKit.tenantUser().isDisable(2001L, "ban")).isTrue();
        StpKit.tenantUser().untieDisable(2001L, "ban");
        assertThat(StpKit.tenantUser().isDisable(2001L, "ban")).isFalse();
    }

    @Test
    void deviceOverflowKicksOldestToken() {
        config.set("modality.security.device.max-count-per-type", "2");
        String first = tokenOf(LoginType.TENANT_USER, "user", PASSWORD, "PC", "d1", null);
        String second = tokenOf(LoginType.TENANT_USER, "user", PASSWORD, "PC", "d2", null);
        String third = tokenOf(LoginType.TENANT_USER, "user", PASSWORD, "PC", "d3", null);
        var online = get("/test/admin/online?type=TENANT_USER&loginId=2001", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"));
        assertThat(JsonPath.parse(online.body()).<Integer>read("$.data.length()")).isEqualTo(2);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, third).statusCode()).isEqualTo(200);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, first).statusCode()).isEqualTo(401);
        assertThat(messageOf(get("/test/user/me", LoginType.TENANT_USER, first))).isEqualTo("token 已被踢下线");
    }

    @Test
    void kickedTokenHasRecordedReason() {
        config.set("modality.security.device.max-count-per-type", "1");
        String first = tokenOf(LoginType.TENANT_USER, "user", PASSWORD, "PC", "d1", null);
        tokenOf(LoginType.TENANT_USER, "user", PASSWORD, "PC", "d2", null);
        String reason = redis.opsForValue().get(SecurityKeys.kickReasonKey(first));
        assertThat(reason).contains("设备登录数量超限");
        Long ttl = redis.getExpire(SecurityKeys.kickReasonKey(first));
        assertThat(ttl).isGreaterThan(0);
    }

    @Test
    void deviceOverflowRejectModeBlocksThirdLogin() {
        config.set("modality.security.device.max-count-per-type", "2");
        config.set("modality.security.device.overflow-mode", "REJECT");
        tokenOf(LoginType.TENANT_USER, "user", PASSWORD, "PC", "d1", null);
        tokenOf(LoginType.TENANT_USER, "user", PASSWORD, "PC", "d2", null);
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"PC","deviceId":"d3"}
                """);
        assertThat(codeOf(response)).isEqualTo(400100);
        assertThat(messageOf(response)).contains("登录数量已达上限");
    }

    @Test
    void differentDeviceTypesCountSeparately() {
        config.set("modality.security.device.max-count-per-type", "1");
        tokenOf(LoginType.TENANT_USER, "user", PASSWORD, "PC", "d1", null);
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"MOBILE","deviceId":"m1"}
                """);
        assertThat(response.body()).contains("tokenValue");
    }

    @Test
    void singleSessionReplacesOldTokenWithBeReplaced() {
        config.set("modality.security.device.single-session-per-type", "true");
        String oldToken = tokenOf(LoginType.TENANT_USER, "user");
        String newToken = tokenOf(LoginType.TENANT_USER, "user");
        assertThat(oldToken).isNotEqualTo(newToken);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, newToken).statusCode()).isEqualTo(200);
        var old = get("/test/user/me", LoginType.TENANT_USER, oldToken);
        assertThat(old.statusCode()).isEqualTo(401);
        assertThat(messageOf(old)).isEqualTo("token 已被顶下线");
    }

    @Test
    void kickServiceRecordsReasonAndDecrementsTerminals() {
        String token = tokenOf(LoginType.TENANT_USER, "user");
        long kickBefore = counters.kickOutCount();
        post("/test/admin/kick?type=TENANT_USER&loginId=2001&token=" + token + "&reason=指定token踢出",
                LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"), null);
        assertThat(counters.kickOutCount()).isEqualTo(kickBefore + 1);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, token).statusCode()).isEqualTo(401);
        assertThat(redis.opsForValue().get(SecurityKeys.kickReasonKey(token))).isEqualTo("指定token踢出");
    }

    @Test
    void kickedTerminalRemovedFromOnlineList() {
        String first = tokenOf(LoginType.TENANT_USER, "user");
        String second = tokenOf(LoginType.TENANT_USER, "user", PASSWORD, "PC", "second", null);
        post("/test/admin/kick?type=TENANT_USER&loginId=2001&deviceType=PC&reason=踢最早",
                LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"), null);
        var online = get("/test/admin/online?type=TENANT_USER&loginId=2001", LoginType.PLATFORM_ADMIN, tokenOf(LoginType.PLATFORM_ADMIN, "admin"));
        List<Map<String, Object>> remaining = JsonPath.parse(online.body()).read("$.data");
        assertThat(remaining).noneMatch(item -> "second".equals(item.get("deviceId")));
        assertThat(get("/test/user/me", LoginType.TENANT_USER, second).statusCode()).isEqualTo(401);
        assertThat(get("/test/user/me", LoginType.TENANT_USER, first).statusCode()).isEqualTo(200);
    }
}
