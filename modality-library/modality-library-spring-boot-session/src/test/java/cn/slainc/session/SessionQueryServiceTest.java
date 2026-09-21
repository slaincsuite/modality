package cn.slainc.session;

import cn.slainc.core.enums.LoginType;
import cn.slainc.session.query.SessionTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionQueryServiceTest extends SessionTestSupport {

    @Test
    @DisplayName("按账号查询会话返回全部终端与关键字段")
    void listByLoginIdReturnsAllTerminals() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "device-a", "10.0.0.8",
                "Mozilla/5.0 (Windows NT 10.0) Chrome/120", null);
        loginDirect(LoginType.TENANT_USER, USER_ONE, "app-ios", "device-b", "10.0.0.9", "iPhone", null);

        List<SessionInfo> sessions = queryService.listByLoginId(LoginType.TENANT_USER, USER_ONE);

        assertThat(sessions).hasSize(2);
        SessionInfo first = sessions.stream().filter(item -> item.deviceId().equals("device-a")).findFirst()
                .orElseThrow();
        assertThat(first.loginType()).isEqualTo("TENANT_USER");
        assertThat(first.loginId()).isEqualTo("101");
        assertThat(first.deviceType()).isEqualTo("web");
        assertThat(first.deviceCategory()).isEqualTo("PC");
        assertThat(first.clientIp()).isEqualTo("10.0.0.8");
        assertThat(first.userAgent()).contains("Chrome/120");
        assertThat(first.loginTime()).isNotNull();
        assertThat(first.active()).isTrue();
        assertThat(first.tokenValue()).isEqualTo(SessionTokens.mask(token));
    }

    @Test
    @DisplayName("按账号查询无会话账号返回空列表")
    void blankLoginIdReturnsEmpty() {
        assertThat(queryService.listByLoginId(LoginType.APP, CLIENT_TWO)).isEmpty();
    }

    @Test
    @DisplayName("按账号查询账号为空时抛中文异常")
    void blankLoginIdThrows() {
        assertThatThrownBy(() -> queryService.listByLoginId(LoginType.TENANT_USER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("账号不能为空");
    }

    @Test
    @DisplayName("跨账号体系查询互不可见")
    void queryIsolatedAcrossTypes() {
        loginDirect(LoginType.PLATFORM_ADMIN, ADMIN_ONE, "web");
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        List<SessionInfo> adminSessions = queryService.listByLoginId(LoginType.PLATFORM_ADMIN, ADMIN_ONE);
        List<SessionInfo> userSessions = queryService.listByLoginId(LoginType.TENANT_USER, USER_ONE);

        assertThat(adminSessions).hasSize(1);
        assertThat(adminSessions.get(0).loginType()).isEqualTo("PLATFORM_ADMIN");
        assertThat(userSessions).hasSize(1);
        assertThat(userSessions.get(0).loginType()).isEqualTo("TENANT_USER");
        assertThat(queryService.listByLoginId(LoginType.PLATFORM_ADMIN, USER_ONE)).isEmpty();
    }

    @Test
    @DisplayName("按 token 查询会话详情命中活跃会话")
    void detailHitsActiveSession() {
        String token = loginDirect(LoginType.TENANT_USER, USER_TWO, "web", "device-detail");

        SessionInfo info = queryService.detail(LoginType.TENANT_USER, token);

        assertThat(info).isNotNull();
        assertThat(info.loginId()).isEqualTo("102");
        assertThat(info.deviceId()).isEqualTo("device-detail");
        assertThat(info.extended()).containsKeys("ip", "ua", "loginTime", "deviceId", "username");
    }

    @Test
    @DisplayName("按 token 查询已失效会话返回 null")
    void detailInvalidTokenReturnsEmpty() {
        assertThat(queryService.detail(LoginType.TENANT_USER, "not-exists-token")).isNull();
        assertThat(queryService.detail(LoginType.TENANT_USER, "")).isNull();
        assertThat(queryService.detail(LoginType.TENANT_USER, null)).isNull();
    }

    @Test
    @DisplayName("token 默认按掩码展示")
    void tokenMaskedByDefault() {
        loginDirect(LoginType.TENANT_USER, USER_THREE, "web");
        String display = queryService.listByLoginId(LoginType.TENANT_USER, USER_THREE).get(0).tokenValue();

        assertThat(display).contains("******");
        assertThat(sessionConfig.maskToken()).isTrue();
    }

    @Test
    @DisplayName("关闭掩码热配置后 token 明文展示")
    void maskDisabledShowsPlain() {
        String token = loginDirect(LoginType.TENANT_USER, USER_THREE, "web");
        config.set("modality.session.display.mask-token", "false");

        SessionInfo info = queryService.detail(LoginType.TENANT_USER, token);

        assertThat(info.tokenValue()).isEqualTo(token);
        assertThat(sessionConfig.maskToken()).isFalse();
    }

    @Test
    @DisplayName("最后活跃时间缺失时回退登录时间")
    void lastActiveFallsBackToLoginTime() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        deleteLastActive("TENANT_USER", token);

        SessionInfo info = queryService.detail(LoginType.TENANT_USER, token);

        assertThat(info.lastActiveTime()).isEqualTo(info.loginTime());
    }

    @Test
    @DisplayName("最后活跃时间读取 Sa-Token 的 last-active 存储")
    void lastActiveReadsStoredValue() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        long expected = System.currentTimeMillis() - 60_000L;
        setLastActive("TENANT_USER", token, expected);

        SessionInfo info = queryService.detail(LoginType.TENANT_USER, token);

        assertThat(info.lastActiveTime()).isEqualTo(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(expected), ZoneId.systemDefault()));
        assertThat(info.lastActiveTime()).isBefore(info.loginTime());
    }

    @Test
    @DisplayName("按设备标识查询会话")
    void queryByDeviceId() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "device-x");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web", "device-y");

        List<SessionInfo> matched = queryService.byDeviceId(LoginType.TENANT_USER, "device-x");

        assertThat(matched).hasSize(1);
        assertThat(matched.get(0).loginId()).isEqualTo("101");
        assertThat(queryService.byDeviceId(LoginType.TENANT_USER, "device-none")).isEmpty();
    }

    @Test
    @DisplayName("按设备标识查询为空时抛中文异常")
    void blankDeviceIdThrows() {
        assertThatThrownBy(() -> queryService.byDeviceId(LoginType.TENANT_USER, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("设备标识不能为空");
    }

    @Test
    @DisplayName("按设备类型查询支持原始类型")
    void deviceTypeRawValue() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "device-w");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "app-android", "device-m");

        List<SessionInfo> matched = queryService.byDeviceType(LoginType.TENANT_USER, "web");

        assertThat(matched).extracting(SessionInfo::loginId).containsExactly("101");
    }

    @Test
    @DisplayName("按设备类型查询支持归一后的类别")
    void deviceTypeNormalizedCategory() {
        loginDirect(LoginType.TENANT_USER, USER_ONE, "app-android", "device-m");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web", "device-w");
        loginDirect(LoginType.TENANT_USER, USER_THREE, "ipad", "device-t");

        assertThat(queryService.byDeviceType(LoginType.TENANT_USER, "MOBILE")).extracting(SessionInfo::loginId)
                .containsExactly("101");
        assertThat(queryService.byDeviceType(LoginType.TENANT_USER, "TABLET")).extracting(SessionInfo::loginId)
                .containsExactly("103");
        assertThat(queryService.byDeviceType(LoginType.TENANT_USER, "PC")).extracting(SessionInfo::loginId)
                .containsExactly("102");
    }

    @Test
    @DisplayName("被踢出的会话不再出现在账号会话列表")
    void kickedTokenRemovedFromList() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        assertThat(queryService.listByLoginId(LoginType.TENANT_USER, USER_ONE)).hasSize(1);

        kickSessionService.kickoutByToken(LoginType.TENANT_USER, token, "测试踢出");

        assertThat(queryService.listByLoginId(LoginType.TENANT_USER, USER_ONE)).isEmpty();
        assertThat(queryService.detail(LoginType.TENANT_USER, token)).isNull();
    }

    @Test
    @DisplayName("会话信息带出配置的超时秒数与剩余有效期")
    void timeoutAndRemainingExpiry() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        SessionInfo info = queryService.detail(LoginType.TENANT_USER, token);

        assertThat(info.timeoutSeconds()).isEqualTo(securityConfig.sessionTimeout(LoginType.TENANT_USER));
        assertThat(info.remainingSeconds()).isGreaterThan(0).isLessThanOrEqualTo(info.timeoutSeconds());
    }
}
