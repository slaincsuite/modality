package cn.slainc.session;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityKeys;
import cn.slainc.security.event.SecurityAlarmEvent;
import cn.slainc.session.cleanup.CleanupReport;
import cn.slainc.session.cleanup.SessionCleanupService;
import cn.slainc.session.event.SessionCleanedEvent;
import cn.slainc.session.event.SessionExpiredEvent;
import cn.slainc.session.timeout.SessionTimeoutPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCleanupServiceTest extends SessionCleanupEnabledTestSupport {

    @Autowired
    private SessionCleanupService cleanupService;

    @Test
    @DisplayName("清理 token 已失效但残留的终端")
    void 清理残留终端() {
        String alive = loginDirect(LoginType.USER, USER_ONE, "web", "alive-device");
        String expired = loginDirect(LoginType.USER, USER_ONE, "web", "expired-device");
        deleteKey("satoken:USER:token:" + expired);

        CleanupReport report = cleanupService.cleanupExpired(LoginType.USER);

        assertThat(report.removedTerminals()).isEqualTo(1);
        assertThat(queryService.listByLoginId(LoginType.USER, USER_ONE)).hasSize(1);
        assertThat(queryService.listByLoginId(LoginType.USER, USER_ONE).get(0).deviceId()).isEqualTo("alive-device");
        assertThat(queryService.detail(LoginType.USER, alive)).isNotNull();
    }

    @Test
    @DisplayName("清理过期会话不影响活跃会话")
    void 清理不影响活跃会话() {
        loginDirect(LoginType.USER, USER_ONE, "web");
        loginDirect(LoginType.USER, USER_TWO, "web");

        CleanupReport report = cleanupService.cleanupExpired(LoginType.USER);

        assertThat(report.removedTerminals()).isZero();
        assertThat(queryService.listByLoginId(LoginType.USER, USER_ONE)).hasSize(1);
    }

    @Test
    @DisplayName("清理只作用于指定账号体系")
    void 清理按体系隔离() {
        String userToken = loginDirect(LoginType.USER, USER_ONE, "web");
        String clientToken = loginDirect(LoginType.CLIENT, CLIENT_ONE, "web");
        deleteKey("satoken:USER:token:" + userToken);
        deleteKey("satoken:CLIENT:token:" + clientToken);

        CleanupReport report = cleanupService.cleanupExpired(LoginType.USER);

        assertThat(report.removedTerminals()).isEqualTo(1);
        assertThat(queryService.listByLoginId(LoginType.USER, USER_ONE)).isEmpty();
        assertThat(queryService.listByLoginId(LoginType.CLIENT, CLIENT_ONE)).hasSize(1);
        assertThat(redis.hasKey("satoken:CLIENT:session:" + CLIENT_ONE)).isTrue();
    }

    @Test
    @DisplayName("清理后账号会话为空时一并删除会话键")
    void 清理后删除空会话() {
        String token = loginDirect(LoginType.USER, USER_TWO, "web");
        assertThat(redis.hasKey("satoken:USER:session:" + USER_TWO)).isTrue();
        deleteKey("satoken:USER:token:" + token);

        cleanupService.cleanupExpired(LoginType.USER);

        assertThat(redis.hasKey("satoken:USER:session:" + USER_TWO)).isFalse();
    }

    @Test
    @DisplayName("清理残留终端时发布清理事件")
    void 清理事件发布() {
        String token = loginDirect(LoginType.USER, USER_THREE, "web");
        deleteKey("satoken:USER:token:" + token);

        cleanupService.cleanupExpired(LoginType.USER);

        List<SessionCleanedEvent> cleaned = events.of(SessionCleanedEvent.class);
        assertThat(cleaned).hasSize(1);
        assertThat(cleaned.get(0).getTokenValue()).isEqualTo(token);
        assertThat(cleaned.get(0).getLoginId()).isEqualTo("103");
        assertThat(cleaned.get(0).getReason()).contains("清理残留终端");
    }

    @Test
    @DisplayName("空闲超时的会话被强制注销")
    void 空闲超时强制注销() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web", "idle-device");
        setLastActive("USER", token, System.currentTimeMillis() - 3_600_000L);

        CleanupReport report = cleanupService.cleanupZombie(600);

        assertThat(report.idleKicked()).isEqualTo(1);
        assertThat(queryService.detail(LoginType.USER, token)).isNull();
        assertThat(redis.opsForValue().get(SecurityKeys.kickReasonKey(token)))
                .isEqualTo(SessionTimeoutPolicy.IDLE_REASON);
    }

    @Test
    @DisplayName("空闲未超时的会话不被注销")
    void 空闲未超时不动() {
        String token = loginDirect(LoginType.USER, USER_TWO, "web");
        setLastActive("USER", token, System.currentTimeMillis() - 60_000L);

        CleanupReport report = cleanupService.cleanupZombie(600);

        assertThat(report.total()).isZero();
        assertThat(queryService.detail(LoginType.USER, token)).isNotNull();
    }

    @Test
    @DisplayName("空闲时长非正数时跳过僵尸清理")
    void 空闲参数非正跳过() {
        String token = loginDirect(LoginType.USER, USER_TWO, "web");
        setLastActive("USER", token, System.currentTimeMillis() - 900_000L);

        CleanupReport report = cleanupService.cleanupZombie(0);

        assertThat(report.total()).isZero();
        assertThat(queryService.detail(LoginType.USER, token)).isNotNull();
    }

    @Test
    @DisplayName("超过最大生命周期的会话被强制注销")
    void 绝对超时强制注销() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web", "lifetime-device");
        config.set("modality.session.max-lifetime-seconds", "60");
        extender.setExtra(LoginType.USER, token, "loginTime", System.currentTimeMillis() - 120_000L);

        CleanupReport report = cleanupService.cleanupAbsoluteTimeout(LoginType.USER);

        assertThat(report.lifetimeKicked()).isEqualTo(1);
        assertThat(queryService.detail(LoginType.USER, token)).isNull();
        assertThat(redis.opsForValue().get(SecurityKeys.kickReasonKey(token)))
                .isEqualTo(SessionTimeoutPolicy.LIFETIME_REASON);
    }

    @Test
    @DisplayName("未超过最大生命周期的会话不被注销")
    void 绝对超时未命中() {
        String token = loginDirect(LoginType.USER, USER_TWO, "web");
        config.set("modality.session.max-lifetime-seconds", "3600");

        CleanupReport report = cleanupService.cleanupAbsoluteTimeout(LoginType.USER);

        assertThat(report.total()).isZero();
        assertThat(queryService.detail(LoginType.USER, token)).isNotNull();
    }

    @Test
    @DisplayName("超时注销后 token 立即无法鉴权")
    void 超时注销后token失效() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");
        setLastActive("USER", token, System.currentTimeMillis() - 7_200_000L);

        cleanupService.cleanupZombie(600);

        assertThat(get("/demo/user/me", LoginType.USER, token).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("超时注销发布过期事件与中文安全告警")
    void 超时事件与告警() {
        String token = loginDirect(LoginType.USER, USER_THREE, "web");
        setLastActive("USER", token, System.currentTimeMillis() - 7_200_000L);

        cleanupService.cleanupZombie(600);

        List<SessionExpiredEvent> expired = events.of(SessionExpiredEvent.class);
        assertThat(expired).hasSize(1);
        assertThat(expired.get(0).getExpireType()).isEqualTo(SessionCleanupService.IDLE_ALARM_TYPE);
        assertThat(expired.get(0).getReason()).isEqualTo(SessionTimeoutPolicy.IDLE_REASON);

        List<SecurityAlarmEvent> alarms = events.of(SecurityAlarmEvent.class);
        assertThat(alarms).extracting(SecurityAlarmEvent::getAlarmType).contains(SessionCleanupService.IDLE_ALARM_TYPE);
    }

    @Test
    @DisplayName("组合清理同时处理残留终端、空闲超时与绝对超时")
    void 组合清理() {
        String residual = loginDirect(LoginType.USER, USER_ONE, "web", "combo-residual");
        deleteKey("satoken:USER:token:" + residual);
        String idle = loginDirect(LoginType.USER, USER_TWO, "web", "combo-idle");
        setLastActive("USER", idle, System.currentTimeMillis() - 7_200_000L);
        String lifetime = loginDirect(LoginType.USER, USER_THREE, "web", "combo-lifetime");
        config.set("modality.session.max-lifetime-seconds", "60");
        extender.setExtra(LoginType.USER, lifetime, "loginTime", System.currentTimeMillis() - 600_000L);
        setLastActive("USER", lifetime, System.currentTimeMillis());
        config.set("modality.session.cleanup.zombie-idle-seconds", "600");

        CleanupReport report = cleanupService.cleanupAll();

        assertThat(report.removedTerminals()).isEqualTo(1);
        assertThat(report.idleKicked()).isEqualTo(1);
        assertThat(report.lifetimeKicked()).isEqualTo(1);
        assertThat(report.total()).isEqualTo(3);
        assertThat(onlineService.onlineCount(LoginType.USER)).isZero();
    }

    @Test
    @DisplayName("组合清理读取空闲超时热配置")
    void 组合清理读取空闲热配置() {
        String token = loginDirect(LoginType.USER, USER_ONE, "web");
        setLastActive("USER", token, System.currentTimeMillis() - 900_000L);
        config.set("modality.session.idle-timeout-seconds", "600");

        CleanupReport report = cleanupService.cleanupAll();

        assertThat(report.idleKicked()).isEqualTo(1);
        assertThat(queryService.detail(LoginType.USER, token)).isNull();
    }

    @Test
    @DisplayName("清理扫描超过上限时标记截断")
    void 清理扫描截断() {
        String first = loginDirect(LoginType.USER, USER_ONE, "web");
        String second = loginDirect(LoginType.USER, USER_TWO, "web");
        deleteKey("satoken:USER:token:" + first);
        deleteKey("satoken:USER:token:" + second);
        config.set("modality.session.max-scan", "1");

        CleanupReport report = cleanupService.cleanupExpired(LoginType.USER);

        assertThat(report.truncated()).isTrue();
        assertThat(report.message()).contains("扫描已达上限");
        assertThat(report.removedTerminals()).isEqualTo(1);
    }
}
