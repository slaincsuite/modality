package cn.slainc.monitor.satoken;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectiveSaTokenMetricsSourceTest {

    private static final String TOKEN_PREFIX = "satoken:login:token:";

    private static final String SESSION_PREFIX = "satoken:login:session:";

    @Test
    @DisplayName("通过反射统计在线用户数与 Token 数")
    void shouldCountOnlineUsersAndTokens() {
        FakeSaTokenManager.reset(TOKEN_PREFIX + "t1", TOKEN_PREFIX + "t2", SESSION_PREFIX + "1001",
                SESSION_PREFIX + "1002", SESSION_PREFIX + "1003", "satoken:other:key");
        ReflectiveSaTokenMetricsSource source = new ReflectiveSaTokenMetricsSource(
                FakeSaTokenManager.class.getName(), TOKEN_PREFIX, SESSION_PREFIX, 1000, 0L);
        assertThat(source.available()).isTrue();
        assertThat(source.onlineUsers()).isEqualTo(3L);
        assertThat(source.tokenCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("缓存窗口内不重复扫描")
    void shouldCacheSampling() {
        FakeSaTokenManager.reset(SESSION_PREFIX + "1001");
        ReflectiveSaTokenMetricsSource source = new ReflectiveSaTokenMetricsSource(
                FakeSaTokenManager.class.getName(), TOKEN_PREFIX, SESSION_PREFIX, 1000, 60000L);
        assertThat(source.onlineUsers()).isEqualTo(1L);
        FakeSaTokenManager.reset(SESSION_PREFIX + "1001", SESSION_PREFIX + "1002");
        assertThat(source.onlineUsers()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Sa-Token 不在类路径上时返回 0 且不抛异常")
    void shouldDegradeWhenSaTokenMissing() {
        ReflectiveSaTokenMetricsSource source = new ReflectiveSaTokenMetricsSource(
                "cn.slainc.satoken.NotExistsManager", TOKEN_PREFIX, SESSION_PREFIX, 1000, 0L);
        assertThat(source.available()).isFalse();
        assertThat(source.onlineUsers()).isZero();
        assertThat(source.tokenCount()).isZero();
    }

    @Test
    @DisplayName("默认前缀与 scanLimit 兜底")
    void shouldUseDefaults() {
        ReflectiveSaTokenMetricsSource source = ReflectiveSaTokenMetricsSource.satoken(0, -1L);
        assertThat(source.available()).isFalse();
        assertThat(source.onlineUsers()).isZero();
    }
}
