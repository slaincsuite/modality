package cn.slainc.security;

import cn.slainc.core.enums.LoginType;
import cn.slainc.core.exception.BizException;
import cn.slainc.security.restrict.LoginRestrictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRestrictionAcceptanceTest extends SecurityAcceptanceTestBase {

    @Autowired
    LoginRestrictionService restrictionService;

    @Test
    void ipBlacklistRejectsMatchingPrefix() {
        config.set("modality.security.restriction.ip-blacklist", "[\"10.20.\"]");
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":"","clientIp":"10.20.30.40"}
                """);
        assertThat(codeOf(response)).isEqualTo(400100);
        assertThat(messageOf(response)).contains("黑名单");
    }

    @Test
    void ipWhitelistAllowsOnlyListedPrefix() {
        config.set("modality.security.restriction.ip-whitelist", "[\"192.168.\"]");
        var allowed = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":"","clientIp":"192.168.1.9"}
                """);
        assertThat(allowed.body()).contains("tokenValue");
        var rejected = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":"","clientIp":"8.8.8.8"}
                """);
        assertThat(messageOf(rejected)).contains("白名单");
    }

    @Test
    void timeWindowInsideAllowsLogin() {
        LocalTime now = LocalTime.now();
        config.set("modality.security.restriction.time-start", now.minusMinutes(5).toString().substring(0, 5));
        config.set("modality.security.restriction.time-end", now.plusMinutes(5).toString().substring(0, 5));
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(response.body()).contains("tokenValue");
    }

    @Test
    void timeWindowOutsideRejectsLogin() {
        LocalTime now = LocalTime.now();
        config.set("modality.security.restriction.time-start", now.plusMinutes(10).toString().substring(0, 5));
        config.set("modality.security.restriction.time-end", now.plusMinutes(20).toString().substring(0, 5));
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(codeOf(response)).isEqualTo(400100);
        assertThat(messageOf(response)).contains("时间段");
    }

    @Test
    void overnightWindowWrapAround() {
        assertThat(restrictionService.isTimeAllowed("23:00", "06:00", LocalTime.of(23, 30))).isTrue();
        assertThat(restrictionService.isTimeAllowed("23:00", "06:00", LocalTime.of(2, 0))).isTrue();
        assertThat(restrictionService.isTimeAllowed("23:00", "06:00", LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void emptyTimeWindowMeansUnrestricted() {
        assertThat(restrictionService.isTimeAllowed("", "", LocalTime.of(3, 0))).isTrue();
        assertThat(restrictionService.isTimeAllowed(null, null, LocalTime.of(23, 59))).isTrue();
    }

    @Test
    void allowedDeviceTypesRestrictLogin() {
        config.set("modality.security.restriction.allowed-device-types", "[\"PC\",\"MOBILE\"]");
        var ok = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"PC","deviceId":""}
                """);
        assertThat(ok.body()).contains("tokenValue");
        var rejected = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"BOT","deviceId":""}
                """);
        assertThat(messageOf(rejected)).contains("设备类型");
    }

    @Test
    void blankDeviceTypeFallsBackToDefaultAndCanBeRestricted() {
        config.set("modality.security.restriction.allowed-device-types", "[\"PC\"]");
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":""}
                """);
        assertThat(messageOf(response)).contains("设备类型");
    }

    @Test
    void whitelistTakesPrecedenceOverBlacklist() {
        config.set("modality.security.restriction.ip-whitelist", "[\"10.20.\"]");
        config.set("modality.security.restriction.ip-blacklist", "[\"10.20.30.\"]");
        var response = post("/auth/login", LoginType.TENANT_USER, null, """
                {"username":"user","password":"Password#123","deviceType":"","deviceId":"","clientIp":"10.20.30.40"}
                """);
        assertThat(response.body()).contains("tokenValue");
    }

    @Test
    void serviceCheckDirectlyThrowsChineseException() {
        config.set("modality.security.restriction.ip-blacklist", "[\"1.2.3.\"]");
        BizException exception = assertThrows(BizException.class,
                () -> restrictionService.check(LoginType.TENANT_USER, "1.2.3.4", "PC"));
        assertThat(exception.getMessage()).contains("黑名单");
    }
}
