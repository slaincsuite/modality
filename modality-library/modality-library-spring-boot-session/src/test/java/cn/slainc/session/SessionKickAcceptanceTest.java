package cn.slainc.session;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityKeys;
import cn.slainc.session.event.SessionKickedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionKickAcceptanceTest extends SessionTestSupport {

    @Test
    @DisplayName("验收：管理员查询在线会话后踢人，被踢 token 立即 401")
    void 验收踢人后token立即失效() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin1");
        String userToken = tokenOf(LoginType.USER, "user1", "web", "accept-device");
        String otherDeviceToken = tokenOf(LoginType.USER, "user1", "app-android", "accept-device-2");

        assertThat(get("/demo/user/me", LoginType.USER, userToken).statusCode()).isEqualTo(200);

        HttpResponse<String> online = get("/session/online?type=USER&size=50", LoginType.ADMIN, adminToken);
        assertThat(online.statusCode()).isEqualTo(200);
        assertThat(online.body()).contains("\"101\"");

        HttpResponse<String> kicked = post("/session/kick/token?type=USER&token=" + userToken
                + "&reason=管理员强制下线", LoginType.ADMIN, adminToken, null);
        assertThat(kicked.statusCode()).isEqualTo(200);
        assertThat((Boolean) dataOf(kicked, "")).isTrue();

        HttpResponse<String> afterKick = get("/demo/user/me", LoginType.USER, userToken);
        assertThat(afterKick.statusCode()).isEqualTo(401);
        assertThat(codeOf(afterKick)).isEqualTo(401000);
        assertThat(messageOf(afterKick)).contains("已被踢下线");

        assertThat(get("/demo/user/me", LoginType.USER, otherDeviceToken).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("踢单个 token 只影响该会话，同账号其它设备仍在线")
    void 按token踢人只影响该会话() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin1");
        String deviceA = tokenOf(LoginType.USER, "user1", "web", "dev-a");
        String deviceB = tokenOf(LoginType.USER, "user1", "web", "dev-b");

        assertThat(kickSessionService.kickoutByToken(LoginType.USER, deviceA, "只踢一个会话")).isTrue();

        assertThat(get("/demo/user/me", LoginType.USER, deviceA).statusCode()).isEqualTo(401);
        assertThat(get("/demo/user/me", LoginType.USER, deviceB).statusCode()).isEqualTo(200);
        assertThat(queryService.listByLoginId(LoginType.USER, USER_ONE)).hasSize(1);
        assertThat(adminToken).isNotBlank();
    }

    @Test
    @DisplayName("按设备类型踢人时该类型全部下线，其它类型不受影响")
    void 按设备类型踢人() {
        String webOne = loginDirect(LoginType.USER, USER_ONE, "web", "dev-w1");
        String webTwo = loginDirect(LoginType.USER, USER_ONE, "web", "dev-w2");
        String mobile = loginDirect(LoginType.USER, USER_ONE, "app-android", "dev-m1");

        kickSessionService.kickout(LoginType.USER, USER_ONE, "web", "网页端强制下线");

        assertThat(queryService.detail(LoginType.USER, webOne)).isNull();
        assertThat(queryService.detail(LoginType.USER, webTwo)).isNull();
        assertThat(queryService.detail(LoginType.USER, mobile)).isNotNull();
    }

    @Test
    @DisplayName("按账号全量踢人后所有设备 token 均失效")
    void 按账号全量踢人() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin1");
        String first = tokenOf(LoginType.USER, "user1", "web", "all-1");
        String second = tokenOf(LoginType.USER, "user1", "app-ios", "all-2");

        HttpResponse<String> response = post("/session/kick?type=USER&loginId=101&reason=账号全量下线",
                LoginType.ADMIN, adminToken, null);
        assertThat(response.statusCode()).isEqualTo(200);

        assertThat(get("/demo/user/me", LoginType.USER, first).statusCode()).isEqualTo(401);
        assertThat(get("/demo/user/me", LoginType.USER, second).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("负向对照：不带 token 访问受保护端点返回 401")
    void 无token访问受保护端点() {
        HttpResponse<String> response = get("/demo/user/me", LoginType.USER, null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(codeOf(response)).isEqualTo(401000);
    }

    @Test
    @DisplayName("负向对照：普通用户 token 不能访问管理端会话接口")
    void 普通用户不能访问管理接口() {
        String userToken = tokenOf(LoginType.USER, "user1", "web", "no-admin");

        HttpResponse<String> response = get("/session/count?type=USER", LoginType.ADMIN, userToken);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("负向对照：跨体系踢人互不影响")
    void 跨体系踢人互不影响() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin1");
        String userToken = tokenOf(LoginType.USER, "user1", "web", "cross-user");
        String clientToken = tokenOf(LoginType.CLIENT, "client1", "web", "cross-client");

        kickSessionService.kickoutByToken(LoginType.USER, userToken, "只踢用户端");

        assertThat(get("/demo/user/me", LoginType.USER, userToken).statusCode()).isEqualTo(401);
        assertThat(get("/demo/admin/panel", LoginType.ADMIN, adminToken).statusCode()).isEqualTo(200);
        assertThat(get("/demo/client/info", LoginType.CLIENT, clientToken).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("踢人原因写入 security 的原因键")
    void 踢人原因写入Redis() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");

        kickSessionService.kickoutByToken(LoginType.USER, token, "违规操作，强制下线");

        String stored = redis.opsForValue().get(SecurityKeys.kickReasonKey(token));
        assertThat(stored).isEqualTo("违规操作，强制下线");
        assertThat(kickSessionService.kickReason(LoginType.USER, token)).isEqualTo("违规操作，强制下线");
    }

    @Test
    @DisplayName("踢人后通知 SPI 收到中文下线提示")
    void 通知SPI收到中文提示() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web", "notify-device");

        kickSessionService.kickoutByToken(LoginType.USER, token, "管理员强制下线");

        assertThat(notifier.notices()).hasSize(1);
        SessionKickNotice notice = notifier.notices().get(0);
        assertThat(notice.loginId()).isEqualTo("101");
        assertThat(notice.loginType()).isEqualTo("USER");
        assertThat(notice.reason()).isEqualTo("管理员强制下线");
        assertThat(notice.message()).contains("您的会话已被下线");
        assertThat(notice.toPayload().get("type")).isEqualTo("session-kicked");
        assertThat(notice.toJson()).contains("您的会话已被下线");
    }

    @Test
    @DisplayName("踢人后 WebSocket 通道按账号推送中文 JSON")
    void WebSocket推送中文JSON() {
        String token = loginDirect(LoginType.USER, USER_TWO, "web", "ws-device");

        kickSessionService.kickoutByToken(LoginType.USER, token, "管理员强制下线");

        assertThat(broadcaster.userMessages()).hasSize(1);
        assertThat(broadcaster.userMessages().get(0)[0]).isEqualTo("102");
        assertThat(broadcaster.userMessages().get(0)[1]).contains("您的会话已被下线").contains("管理员强制下线");
    }

    @Test
    @DisplayName("踢人事件按会话粒度发布")
    void 踢人事件发布() {
        String first = loginDirect(LoginType.USER, USER_ONE, "web", "evt-1");
        String second = loginDirect(LoginType.USER, USER_ONE, "web", "evt-2");

        kickSessionService.kickout(LoginType.USER, USER_ONE, "批量下线");

        List<SessionKickedEvent> published = events.of(SessionKickedEvent.class);
        assertThat(published).hasSize(2);
        assertThat(published).extracting(SessionKickedEvent::getTokenValue)
                .containsExactlyInAnyOrder(first, second);
        assertThat(published.get(0).getReason()).isEqualTo("批量下线");
        assertThat(published.get(0).getSource()).isEqualTo("session-kick");
    }

    @Test
    @DisplayName("批量踢人按账号列表逐个下线")
    void 批量踢人() {
        loginDirect(LoginType.USER, USER_ONE, "web");
        loginDirect(LoginType.USER, USER_TWO, "web");
        loginDirect(LoginType.USER, USER_THREE, "web");

        int count = kickSessionService.batchKickout(LoginType.USER, List.of(USER_ONE, USER_TWO), "批量清理");

        assertThat(count).isEqualTo(2);
        assertThat(queryService.listByLoginId(LoginType.USER, USER_ONE)).isEmpty();
        assertThat(queryService.listByLoginId(LoginType.USER, USER_TWO)).isEmpty();
        assertThat(queryService.listByLoginId(LoginType.USER, USER_THREE)).hasSize(1);
    }

    @Test
    @DisplayName("按租户踢人只影响该租户的在线会话")
    void 按租户踢人() {
        String tenantOne = loginDirect(LoginType.USER, USER_ONE, "web", "t-1", "127.0.0.1", null, "1001");
        String tenantOneOther = loginDirect(LoginType.USER, USER_TWO, "web", "t-2", "127.0.0.1", null, "1001");
        String tenantTwo = loginDirect(LoginType.USER, USER_THREE, "web", "t-3", "127.0.0.1", null, "1002");

        int count = kickSessionService.kickoutByTenant(LoginType.USER, "1001", "租户下线");

        assertThat(count).isEqualTo(2);
        assertThat(queryService.detail(LoginType.USER, tenantOne)).isNull();
        assertThat(queryService.detail(LoginType.USER, tenantOneOther)).isNull();
        assertThat(queryService.detail(LoginType.USER, tenantTwo)).isNotNull();
    }

    @Test
    @DisplayName("按设备标识踢人只影响指定设备")
    void 按设备标识踢人() {
        String deviceA = loginDirect(LoginType.USER, USER_ONE, "web", "kick-device-a");
        String deviceB = loginDirect(LoginType.USER, USER_ONE, "web", "kick-device-b");

        int count = kickSessionService.kickoutByDevice(LoginType.USER, USER_ONE, "kick-device-a", "设备丢失下线");

        assertThat(count).isEqualTo(1);
        assertThat(queryService.detail(LoginType.USER, deviceA)).isNull();
        assertThat(queryService.detail(LoginType.USER, deviceB)).isNotNull();
    }

    @Test
    @DisplayName("强制注销委托 security 的注销并清空该账号会话")
    void 强制注销() {
        String first = loginDirect(LoginType.USER, USER_ONE, "web", "force-1");
        String second = loginDirect(LoginType.USER, USER_ONE, "web", "force-2");

        int count = kickSessionService.forceLogout(LoginType.USER, USER_ONE, "离职强制注销");

        assertThat(count).isEqualTo(2);
        assertThat(queryService.detail(LoginType.USER, first)).isNull();
        assertThat(queryService.detail(LoginType.USER, second)).isNull();
        assertThat(notifier.notices()).hasSize(2);
    }

    @Test
    @DisplayName("两参强制注销使用默认中文原因")
    void 两参强制注销() {
        loginDirect(LoginType.USER, USER_TWO, "web", "force-default");

        int count = kickSessionService.forceLogout(LoginType.USER, USER_TWO);

        assertThat(count).isEqualTo(1);
        assertThat(notifier.notices().get(0).reason()).isEqualTo("管理员强制下线");
    }

    @Test
    @DisplayName("踢不存在的 token 不抛异常且返回 false")
    void 踢不存在的token() {
        assertThat(kickSessionService.kickoutByToken(LoginType.USER, "no-such-token", "无效会话")).isFalse();
        assertThat(notifier.notices()).isEmpty();
    }

    @Test
    @DisplayName("踢人后在线列表不再包含该会话")
    void 踢人后在线列表更新() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin1");
        String token = tokenOf(LoginType.USER, "user1", "web", "list-device");
        assertThat((Integer) dataOf(get("/session/online?type=USER", LoginType.ADMIN, adminToken), "page.total"))
                .isEqualTo(1);

        kickSessionService.kickoutByToken(LoginType.USER, token, "列表校验");

        assertThat((Integer) dataOf(get("/session/online?type=USER", LoginType.ADMIN, adminToken), "page.total"))
                .isZero();
    }

    @Test
    @DisplayName("会话统计接口返回体系与设备维度")
    void 统计接口() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin1");
        String userToken = tokenOf(LoginType.USER, "user1", "app-android", "stats-device");

        HttpResponse<String> response = get("/session/stats?type=USER", LoginType.ADMIN, adminToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) dataOf(response, "total")).isEqualTo("1");
        assertThat(response.body()).contains("MOBILE").contains("未归属");
        assertThat(userToken).isNotBlank();
    }

    @Test
    @DisplayName("CSV 导出接口返回带 BOM 的文本")
    void CSV导出接口() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin1");
        tokenOf(LoginType.USER, "user1", "web", "export-device");

        HttpResponse<String> response = get("/session/export.csv?type=USER", LoginType.ADMIN, adminToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith("\uFEFF");
        assertThat(response.body()).contains("账号体系,登录账号,Token");
        assertThat(response.body()).contains("101");
    }

    @Test
    @DisplayName("客户端体系会话可被独立踢出且不影响其它体系")
    void 客户端体系踢人() {
        String clientToken = loginDirect(LoginType.CLIENT, CLIENT_ONE, "web", "client-device");
        loginDirect(LoginType.USER, USER_ONE, "web", "user-device");

        assertThat(kickSessionService.kickoutByToken(LoginType.CLIENT, clientToken, "客户端下线")).isTrue();

        assertThat(get("/demo/client/info", LoginType.CLIENT, clientToken).statusCode()).isEqualTo(401);
        assertThat(queryService.listByLoginId(LoginType.CLIENT, CLIENT_ONE)).isEmpty();
        assertThat(queryService.listByLoginId(LoginType.USER, USER_ONE)).hasSize(1);
    }

    @Test
    @DisplayName("参数非法时给出中文异常")
    void 参数校验() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> kickSessionService.kickout(LoginType.USER, null, "原因"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("账号不能为空");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> kickSessionService.kickoutByTenant(LoginType.USER, " ", "原因"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("租户标识不能为空");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> kickSessionService.kickoutByDevice(LoginType.USER, USER_ONE, "", "原因"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("设备标识不能为空");
    }

    @Test
    @DisplayName("管理端会话接口在 token 被踢后立即 401")
    void 管理端token被踢后立即失效() {
        String adminToken = loginDirect(LoginType.ADMIN, ADMIN_TWO, "web", "admin-device");
        assertThat(get("/session/count?type=USER", LoginType.ADMIN, adminToken).statusCode()).isEqualTo(200);

        kickSessionService.kickoutByToken(LoginType.ADMIN, adminToken, "管理员会话下线");

        HttpResponse<String> afterKick = get("/session/count?type=USER", LoginType.ADMIN, adminToken);
        assertThat(afterKick.statusCode()).isEqualTo(401);
        assertThat(codeOf(afterKick)).isEqualTo(401000);
    }

    @Test
    @DisplayName("按设备标识查询接口返回会话列表")
    void 按设备标识查询接口() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin1");
        tokenOf(LoginType.USER, "user1", "web", "query-device");

        HttpResponse<String> response = get("/session/by-device?type=USER&deviceId=query-device",
                LoginType.ADMIN, adminToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("query-device").contains("\"101\"");
    }

    @Test
    @DisplayName("会话详情接口默认掩码展示 token")
    void 详情接口() {
        String adminToken = tokenOf(LoginType.ADMIN, "admin1");
        String userToken = tokenOf(LoginType.USER, "user1", "web", "detail-device");

        HttpResponse<String> response = get("/session/detail?type=USER&token=" + userToken,
                LoginType.ADMIN, adminToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"101\"").contains("******");
        assertThat(response.body()).doesNotContain(userToken);
    }

    @Test
    @DisplayName("无 token 访问管理端会话接口返回 401")
    void 管理端无token() {
        Map<String, String> headers = Map.of();
        assertThat(get("/session/online", LoginType.ADMIN, null).statusCode()).isEqualTo(401);
        assertThat(headers).isEmpty();
    }
}
