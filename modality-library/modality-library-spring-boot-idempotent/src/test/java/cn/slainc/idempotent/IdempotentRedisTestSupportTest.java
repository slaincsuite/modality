package cn.slainc.idempotent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentRedisTestSupportTest {

    @Test
    void 不可达端口快速失败() {
        long start = System.currentTimeMillis();
        assertThat(IdempotentRedisTestSupport.probe("127.0.0.1", 1, "whatever")).isFalse();
        assertThat(System.currentTimeMillis() - start).as("探测必须在 3 秒内结束，不能挂死").isLessThan(3000L);
    }

    @Test
    void 不可达原因使用中文并指明快速失败() {
        String reason = IdempotentRedisTestSupport.unavailableReason("127.0.0.1", 6379, "omni-redis-pass");
        assertThat(reason).contains("没有可用的 Redis").contains("真实 Redis").contains("快速失败");
    }
}
