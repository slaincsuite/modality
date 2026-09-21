package cn.slainc.session;

import cn.slainc.core.enums.LoginType;
import cn.slainc.security.config.SecurityKeys;
import cn.slainc.session.cleanup.SessionCleanupScheduler;
import cn.slainc.session.kick.KickSessionService;
import cn.slainc.session.limit.SessionLimitService;
import cn.slainc.session.timeout.SessionTimeoutPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionExtendRenewalTest extends SessionTestSupport {

    @Autowired
    private SessionLimitService limitService;

    @Autowired
    private SessionTimeoutPolicy timeoutPolicy;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("写入终端扩展字段后可立即读回")
    void setExtraWriteAndRead() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        boolean updated = extender.setExtra(LoginType.TENANT_USER, token, "channel", "小程序");

        assertThat(updated).isTrue();
        assertThat(extender.getExtra(LoginType.TENANT_USER, token, "channel")).isEqualTo("小程序");
    }

    @Test
    @DisplayName("扩展字段写入后出现在会话详情的 extended 中")
    void setExtraVisibleInDetail() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        extender.setExtra(LoginType.TENANT_USER, token, "tenantId", "2001");

        assertThat(queryService.detail(LoginType.TENANT_USER, token).extended()).containsEntry("tenantId", "2001");
        assertThat(queryService.detail(LoginType.TENANT_USER, token).tenantId()).isEqualTo(2001L);
    }

    @Test
    @DisplayName("批量写入扩展字段")
    void setExtrasBatchWrite() {
        String token = loginDirect(LoginType.APP, CLIENT_ONE, "web");

        extender.setExtras(LoginType.APP, token, Map.of("channel", "开放平台", "level", "3"));

        Map<String, Object> extra = extender.extraData(LoginType.APP, token);
        assertThat(extra).containsEntry("channel", "开放平台").containsEntry("level", "3");
    }

    @Test
    @DisplayName("删除扩展字段")
    void removeExtraDeletes() {
        String token = loginDirect(LoginType.TENANT_USER, USER_TWO, "web");
        extender.setExtra(LoginType.TENANT_USER, token, "temp", "1");

        assertThat(extender.removeExtra(LoginType.TENANT_USER, token, "temp")).isTrue();
        assertThat(extender.getExtra(LoginType.TENANT_USER, token, "temp")).isNull();
        assertThat(extender.removeExtra(LoginType.TENANT_USER, token, "temp")).isFalse();
    }

    @Test
    @DisplayName("对无效 token 写入扩展字段返回 false")
    void setExtraInvalidToken() {
        assertThat(extender.setExtra(LoginType.TENANT_USER, "no-such-token", "key", "value")).isFalse();
        assertThat(extender.extraData(LoginType.TENANT_USER, "no-such-token")).isEmpty();
        assertThat(extender.getExtra(LoginType.TENANT_USER, "no-such-token", "key")).isNull();
    }

    @Test
    @DisplayName("扩展字段名称为空时抛中文异常")
    void setExtraBlankKey() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        assertThatThrownBy(() -> extender.setExtra(LoginType.TENANT_USER, token, " ", "value"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("扩展字段名不能为空");
        assertThatThrownBy(() -> extender.setExtras(LoginType.TENANT_USER, token, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("扩展字段不能为空");
    }

    @Test
    @DisplayName("续期后剩余有效期变长")
    void expiryExtendedAfterRenewal() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        long renewed = renewalService.renew(LoginType.TENANT_USER, token, 7200);

        assertThat(renewed).isBetween(7100L, 7200L);
        assertThat(queryService.detail(LoginType.TENANT_USER, token).remainingSeconds()).isGreaterThan(7000L);
    }

    @Test
    @DisplayName("续期参数非法时抛中文异常")
    void renewalParamValidation() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        assertThatThrownBy(() -> renewalService.renew(LoginType.TENANT_USER, token, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("续期时长必须大于 0 秒");
        assertThatThrownBy(() -> renewalService.renew(LoginType.TENANT_USER, " ", 60))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("token 不能为空");
    }

    @Test
    @DisplayName("活跃时按阈值滑动续期")
    void renewIfNeededSlidingRenewal() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");

        assertThat(renewalService.renewIfNeeded(LoginType.TENANT_USER, token, 100, 3600)).isFalse();
        assertThat(renewalService.renewIfNeeded(LoginType.TENANT_USER, token, 2592000, 3600)).isTrue();
        assertThat(remainingSeconds(token)).isBetween(3500L, 3600L);
    }

    @Test
    @DisplayName("按热配置校准会话有效期")
    void expiryCalibratedByHotConfig() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        config.set("modality.session.ttl-seconds", "120");

        long applied = renewalService.applyConfiguredTtl(LoginType.TENANT_USER, token);

        assertThat(applied).isBetween(110L, 120L);
        assertThat(renewalService.effectiveTtlSeconds(LoginType.TENANT_USER)).isEqualTo(120L);
    }

    @Test
    @DisplayName("未配置模块 TTL 时沿用 security 的会话超时")
    void securityTimeoutInheritedByDefault() {
        assertThat(renewalService.effectiveTtlSeconds(LoginType.TENANT_USER))
                .isEqualTo(securityConfig.sessionTimeout(LoginType.TENANT_USER));
        assertThat(renewalService.effectiveTtlSeconds(LoginType.PLATFORM_ADMIN))
                .isEqualTo(securityConfig.sessionTimeout(LoginType.PLATFORM_ADMIN));
    }

    @Test
    @DisplayName("超时策略识别空闲与绝对超时")
    void timeoutPolicyEvaluation() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web");
        setLastActive("TENANT_USER", token, System.currentTimeMillis() - 3_600_000L);
        SessionInfo idle = queryService.detail(LoginType.TENANT_USER, token);

        assertThat(timeoutPolicy.idleExceeded(idle, 600)).isTrue();
        assertThat(timeoutPolicy.idleExceeded(idle, 7200)).isFalse();
        assertThat(timeoutPolicy.absoluteExpired(idle)).isFalse();

        extender.setExtra(LoginType.TENANT_USER, token, "loginTime", System.currentTimeMillis() - 90_000L);
        config.set("modality.session.max-lifetime-seconds", "60");
        SessionInfo expired = queryService.detail(LoginType.TENANT_USER, token);

        assertThat(timeoutPolicy.absoluteExpired(expired)).isTrue();
        assertThat(timeoutPolicy.remainingLifetimeSeconds(expired)).isNegative();
    }

    @Test
    @DisplayName("超过单账号会话上限时自动踢出最早登录")
    void limitOverflowKicksOldest() {
        config.set("modality.session.max-sessions-per-user", "2");
        String first = loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "limit-a");
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "limit-b");
        loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "limit-c");

        assertThat(limitService.enforce(LoginType.TENANT_USER, USER_ONE)).isEqualTo(1);
        assertThat(limitService.maxSessionsPerUser()).isEqualTo(2);
        assertThat(queryService.listByLoginId(LoginType.TENANT_USER, USER_ONE)).hasSize(2);
        assertThat(queryService.detail(LoginType.TENANT_USER, first)).isNull();
        assertThat(redis.opsForValue().get(SecurityKeys.kickReasonKey(first)))
                .isEqualTo(SessionLimitService.OVERFLOW_REASON);
    }

    @Test
    @DisplayName("真实登录触发会话上限校验")
    void loginTriggersLimitCheck() {
        config.set("modality.session.max-sessions-per-user", "1");
        String oldest = tokenOf(LoginType.TENANT_USER, "user2", "web", "http-limit-a");
        String newest = tokenOf(LoginType.TENANT_USER, "user2", "web", "http-limit-b");

        assertThat(queryService.detail(LoginType.TENANT_USER, oldest)).isNull();
        assertThat(queryService.detail(LoginType.TENANT_USER, newest)).isNotNull();
        assertThat(get("/demo/user/me", LoginType.TENANT_USER, newest).statusCode()).isEqualTo(200);
        assertThat(get("/demo/user/me", LoginType.TENANT_USER, oldest).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("会话上限为 0 时不限制登录数量")
    void zeroLimitMeansUnlimited() {
        config.set("modality.session.max-sessions-per-user", "0");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web", "no-limit-a");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web", "no-limit-b");
        loginDirect(LoginType.TENANT_USER, USER_TWO, "web", "no-limit-c");

        assertThat(limitService.enforce(LoginType.TENANT_USER, USER_TWO)).isZero();
        assertThat(queryService.listByLoginId(LoginType.TENANT_USER, USER_TWO)).hasSize(3);
    }

    @Test
    @DisplayName("默认关闭定时清理，主上下文不含调度器")
    void schedulerNotRegisteredByDefault() {
        assertThat(applicationContext.getBeanNamesForType(SessionCleanupScheduler.class)).isEmpty();
        assertThat(applicationContext.getBeanNamesForType(KickSessionService.class)).hasSize(1);
    }

    @Test
    @DisplayName("扩展字段与会话对象隔离，不影响其它会话")
    void extrasIsolatedAcrossSessions() {
        String first = loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "iso-a");
        String second = loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "iso-b");

        extender.setExtra(LoginType.TENANT_USER, first, "only", "a");

        assertThat(extender.getExtra(LoginType.TENANT_USER, first, "only")).isEqualTo("a");
        assertThat(extender.getExtra(LoginType.TENANT_USER, second, "only")).isNull();
    }

    @Test
    @DisplayName("扩展字段不会覆盖登录期写入的内置字段")
    void extrasKeepBuiltinFields() {
        String token = loginDirect(LoginType.TENANT_USER, USER_ONE, "web", "keep-device", "10.2.2.2", "Agent-X", null);

        extender.setExtra(LoginType.TENANT_USER, token, "tenantId", "3001");

        Map<String, Object> extra = extender.extraData(LoginType.TENANT_USER, token);
        assertThat(extra).containsEntry("ip", "10.2.2.2").containsEntry("deviceId", "keep-device")
                .containsEntry("ua", "Agent-X").containsEntry("tenantId", "3001");
        assertThat(extra.get("loginTime")).isNotNull();
    }

    private long remainingSeconds(String token) {
        return cn.slainc.security.StpKit.of(LoginType.TENANT_USER).getTokenTimeout(token);
    }

    @Test
    @DisplayName("续期空 token 返回 false 且不抛异常")
    void slidingRenewalBlankToken() {
        assertThat(renewalService.renewIfNeeded(LoginType.TENANT_USER, null, 60, 3600)).isFalse();
    }

    @Test
    @DisplayName("续期不改变登录时间，扩展字段仍可写")
    void renewalAndExtrasIndependent() {
        String token = loginDirect(LoginType.TENANT_USER, USER_TWO, "web");
        LocalDateTime loginTime = queryService.detail(LoginType.TENANT_USER, token).loginTime();

        renewalService.renew(LoginType.TENANT_USER, token, 600);
        extender.setExtra(LoginType.TENANT_USER, token, "mark", "续期后写入");

        SessionInfo after = queryService.detail(LoginType.TENANT_USER, token);
        assertThat(after.loginTime()).isEqualTo(loginTime);
        assertThat(after.remainingSeconds()).isBetween(500L, 600L);
        assertThat(after.extended()).containsEntry("mark", "续期后写入");
    }
}
