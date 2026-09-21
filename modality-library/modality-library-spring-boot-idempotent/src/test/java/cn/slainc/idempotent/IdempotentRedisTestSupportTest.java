package cn.slainc.idempotent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotentRedisTestSupportTest {

    @Test
    void unreachablePortFailsFast() {
        long start = System.currentTimeMillis();
        assertThat(IdempotentRedisTestSupport.probe("127.0.0.1", 1, "whatever")).isFalse();
        assertThat(System.currentTimeMillis() - start).as("探测必须在 3 秒内结束，不能挂死").isLessThan(3000L);
    }

    @Test
    void unreachableReasonUsesChineseAndFailFast() {
        String reason = IdempotentRedisTestSupport.unavailableReason("127.0.0.1", 6379, "omni-redis-pass");
        assertThat(reason).contains("没有可用的 Redis").contains("真实 Redis").contains("快速失败");
    }
}
