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
    void listByLoginId返回全部终端() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web", "device-a", "10.0.0.8",
                "Mozilla/5.0 (Windows NT 10.0) Chrome/120", null);
        loginDirect(LoginType.USER, USER_ONE, "app-ios", "device-b", "10.0.0.9", "iPhone", null);

        List<SessionInfo> sessions = queryService.listByLoginId(LoginType.USER, USER_ONE);

        assertThat(sessions).hasSize(2);
        SessionInfo first = sessions.stream().filter(item -> item.deviceId().equals("device-a")).findFirst()
                .orElseThrow();
        assertThat(first.loginType()).isEqualTo("USER");
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
    void listByLoginId空账号返回空() {
        assertThat(queryService.listByLoginId(LoginType.CLIENT, CLIENT_TWO)).isEmpty();
    }

    @Test
    @DisplayName("按账号查询账号为空时抛中文异常")
    void listByLoginId账号为空抛异常() {
        assertThatThrownBy(() -> queryService.listByLoginId(LoginType.USER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("账号不能为空");
    }

    @Test
    @DisplayName("跨账号体系查询互不可见")
    void 跨体系查询隔离() {
        loginDirect(LoginType.ADMIN, ADMIN_ONE, "web");
        loginDirect(LoginType.USER, USER_ONE, "web");

        List<SessionInfo> adminSessions = queryService.listByLoginId(LoginType.ADMIN, ADMIN_ONE);
        List<SessionInfo> userSessions = queryService.listByLoginId(LoginType.USER, USER_ONE);

        assertThat(adminSessions).hasSize(1);
        assertThat(adminSessions.get(0).loginType()).isEqualTo("ADMIN");
        assertThat(userSessions).hasSize(1);
        assertThat(userSessions.get(0).loginType()).isEqualTo("USER");
        assertThat(queryService.listByLoginId(LoginType.ADMIN, USER_ONE)).isEmpty();
    }

    @Test
    @DisplayName("按 token 查询会话详情命中活跃会话")
    void detail命中活跃会话() {
        String token = loginDirect(LoginType.USER, USER_TWO, "web", "device-detail");

        SessionInfo info = queryService.detail(LoginType.USER, token);

        assertThat(info).isNotNull();
        assertThat(info.loginId()).isEqualTo("102");
        assertThat(info.deviceId()).isEqualTo("device-detail");
        assertThat(info.extended()).containsKeys("ip", "ua", "loginTime", "deviceId", "username");
    }

    @Test
    @DisplayName("按 token 查询已失效会话返回 null")
    void detail无效token返回空() {
        assertThat(queryService.detail(LoginType.USER, "not-exists-token")).isNull();
        assertThat(queryService.detail(LoginType.USER, "")).isNull();
        assertThat(queryService.detail(LoginType.USER, null)).isNull();
    }

    @Test
    @DisplayName("token 默认按掩码展示")
    void token默认掩码() {
        loginDirect(LoginType.USER, USER_THREE, "web");
        String display = queryService.listByLoginId(LoginType.USER, USER_THREE).get(0).tokenValue();

        assertThat(display).contains("******");
        assertThat(sessionConfig.maskToken()).isTrue();
    }

    @Test
    @DisplayName("关闭掩码热配置后 token 明文展示")
    void 关闭掩码后明文展示() {
        String token = loginDirect(LoginType.USER, USER_THREE, "web");
        config.set("modality.session.display.mask-token", "false");

        SessionInfo info = queryService.detail(LoginType.USER, token);

        assertThat(info.tokenValue()).isEqualTo(token);
        assertThat(sessionConfig.maskToken()).isFalse();
    }

    @Test
    @DisplayName("最后活跃时间缺失时回退登录时间")
    void 最后活跃时间回退登录时间() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");
        deleteLastActive("USER", token);

        SessionInfo info = queryService.detail(LoginType.USER, token);

        assertThat(info.lastActiveTime()).isEqualTo(info.loginTime());
    }

    @Test
    @DisplayName("最后活跃时间读取 Sa-Token 的 last-active 存储")
    void 最后活跃时间读取存储值() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");
        long expected = System.currentTimeMillis() - 60_000L;
        setLastActive("USER", token, expected);

        SessionInfo info = queryService.detail(LoginType.USER, token);

        assertThat(info.lastActiveTime()).isEqualTo(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(expected), ZoneId.systemDefault()));
        assertThat(info.lastActiveTime()).isBefore(info.loginTime());
    }

    @Test
    @DisplayName("按设备标识查询会话")
    void byDeviceId查询() {
        loginDirect(LoginType.USER, USER_ONE, "web", "device-x");
        loginDirect(LoginType.USER, USER_TWO, "web", "device-y");

        List<SessionInfo> matched = queryService.byDeviceId(LoginType.USER, "device-x");

        assertThat(matched).hasSize(1);
        assertThat(matched.get(0).loginId()).isEqualTo("101");
        assertThat(queryService.byDeviceId(LoginType.USER, "device-none")).isEmpty();
    }

    @Test
    @DisplayName("按设备标识查询为空时抛中文异常")
    void byDeviceId空参数抛异常() {
        assertThatThrownBy(() -> queryService.byDeviceId(LoginType.USER, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("设备标识不能为空");
    }

    @Test
    @DisplayName("按设备类型查询支持原始类型")
    void byDeviceType原始类型() {
        loginDirect(LoginType.USER, USER_ONE, "web", "device-w");
        loginDirect(LoginType.USER, USER_TWO, "app-android", "device-m");

        List<SessionInfo> matched = queryService.byDeviceType(LoginType.USER, "web");

        assertThat(matched).extracting(SessionInfo::loginId).containsExactly("101");
    }

    @Test
    @DisplayName("按设备类型查询支持归一后的类别")
    void byDeviceType归一类别() {
        loginDirect(LoginType.USER, USER_ONE, "app-android", "device-m");
        loginDirect(LoginType.USER, USER_TWO, "web", "device-w");
        loginDirect(LoginType.USER, USER_THREE, "ipad", "device-t");

        assertThat(queryService.byDeviceType(LoginType.USER, "MOBILE")).extracting(SessionInfo::loginId)
                .containsExactly("101");
        assertThat(queryService.byDeviceType(LoginType.USER, "TABLET")).extracting(SessionInfo::loginId)
                .containsExactly("103");
        assertThat(queryService.byDeviceType(LoginType.USER, "PC")).extracting(SessionInfo::loginId)
                .containsExactly("102");
    }

    @Test
    @DisplayName("被踢出的会话不再出现在账号会话列表")
    void 被踢后列表不再包含() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");
        assertThat(queryService.listByLoginId(LoginType.USER, USER_ONE)).hasSize(1);

        kickSessionService.kickoutByToken(LoginType.USER, token, "测试踢出");

        assertThat(queryService.listByLoginId(LoginType.USER, USER_ONE)).isEmpty();
        assertThat(queryService.detail(LoginType.USER, token)).isNull();
    }

    @Test
    @DisplayName("会话信息带出配置的超时秒数与剩余有效期")
    void 超时与剩余有效期() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");

        SessionInfo info = queryService.detail(LoginType.USER, token);

        assertThat(info.timeoutSeconds()).isEqualTo(securityConfig.sessionTimeout(LoginType.USER));
        assertThat(info.remainingSeconds()).isGreaterThan(0).isLessThanOrEqualTo(info.timeoutSeconds());
    }
}
