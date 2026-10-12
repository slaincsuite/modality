package cn.slainc.redis.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicConfigVersionTest {

    @Test
    void millisecondTimestampDoesNotWrapToNegative() {
        assertThat(RedisDynamicConfigService.toEntityVersion(1_700_000_000_000L))
                .isEqualTo(Integer.MAX_VALUE);
        assertThat(RedisDynamicConfigService.toEntityVersion(Integer.MAX_VALUE + 5L))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void counterVersionKeepsValueAndNegativeIsClamped() {
        assertThat(RedisDynamicConfigService.toEntityVersion(42L)).isEqualTo(42);
        assertThat(RedisDynamicConfigService.toEntityVersion(0L)).isZero();
        assertThat(RedisDynamicConfigService.toEntityVersion(-1L)).isZero();
    }
}
