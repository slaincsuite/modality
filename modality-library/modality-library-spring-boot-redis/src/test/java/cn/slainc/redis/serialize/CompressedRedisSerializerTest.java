package cn.slainc.redis.serialize;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompressedRedisSerializerTest {

    private final RedisSerializer<String> raw = RedisSerializer.string();

    @Test
    void smallValueNotCompressed() {
        CompressedRedisSerializer<String> serializer = new CompressedRedisSerializer<>(raw, 1024);
        byte[] bytes = serializer.serialize("短内容");
        assertThat(bytes[0]).isEqualTo(CompressedRedisSerializer.MAGIC_RAW);
        assertThat(serializer.deserialize(bytes)).isEqualTo("短内容");
    }

    @Test
    void largeValueCompressedWithMagicHeader() {
        CompressedRedisSerializer<String> serializer = new CompressedRedisSerializer<>(raw, 64);
        String payload = "模态平台".repeat(500);
        byte[] plain = raw.serialize(payload);
        byte[] compressed = serializer.serialize(payload);

        assertThat(compressed[0]).isEqualTo(CompressedRedisSerializer.MAGIC_GZIP);
        assertThat(compressed.length).isLessThan(plain.length);
        assertThat(serializer.deserialize(compressed)).isEqualTo(payload);
    }

    @Test
    void nullAndLegacyNonCompressedParseable() {
        CompressedRedisSerializer<String> serializer = new CompressedRedisSerializer<>(raw, 16);
        assertThat(serializer.serialize(null)).isNull();
        assertThat(serializer.deserialize(null)).isNull();
        assertThat(serializer.deserialize(new byte[0])).isNull();
        assertThat(serializer.deserialize("历史数据".getBytes(StandardCharsets.UTF_8))).isEqualTo("历史数据");
    }

    @Test
    void blankThresholdOrDelegateThrowsChinese() {
        assertThatThrownBy(() -> new CompressedRedisSerializer<String>(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("被包装的序列化器不能为空");

        CompressedRedisSerializer<String> serializer = new CompressedRedisSerializer<>(raw, 0);
        assertThat(serializer.getThreshold()).isEqualTo(1);
        assertThat(serializer.getDelegate()).isSameAs(raw);
        assertThat(serializer.deserialize(serializer.serialize("x"))).isEqualTo("x");
    }
}
