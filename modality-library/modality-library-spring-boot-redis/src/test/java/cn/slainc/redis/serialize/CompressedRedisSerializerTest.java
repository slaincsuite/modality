package cn.slainc.redis.serialize;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompressedRedisSerializerTest {

    private final RedisSerializer<String> raw = RedisSerializer.string();

    @Test
    void 小值使用原始魔术头且不被压缩() {
        CompressedRedisSerializer<String> serializer = new CompressedRedisSerializer<>(raw, 1024);
        byte[] bytes = serializer.serialize("短内容");
        assertThat(bytes[0]).isEqualTo(CompressedRedisSerializer.MAGIC_RAW);
        assertThat(serializer.deserialize(bytes)).isEqualTo("短内容");
    }

    @Test
    void 大值使用压缩魔术头且能还原() {
        CompressedRedisSerializer<String> serializer = new CompressedRedisSerializer<>(raw, 64);
        String payload = "模态平台".repeat(500);
        byte[] plain = raw.serialize(payload);
        byte[] compressed = serializer.serialize(payload);

        assertThat(compressed[0]).isEqualTo(CompressedRedisSerializer.MAGIC_GZIP);
        assertThat(compressed.length).isLessThan(plain.length);
        assertThat(serializer.deserialize(compressed)).isEqualTo(payload);
    }

    @Test
    void 空值与非压缩历史数据都能兼容解析() {
        CompressedRedisSerializer<String> serializer = new CompressedRedisSerializer<>(raw, 16);
        assertThat(serializer.serialize(null)).isNull();
        assertThat(serializer.deserialize(null)).isNull();
        assertThat(serializer.deserialize(new byte[0])).isNull();
        assertThat(serializer.deserialize("历史数据".getBytes(StandardCharsets.UTF_8))).isEqualTo("历史数据");
    }

    @Test
    void 阈值与委托对象为空时抛出中文异常() {
        assertThatThrownBy(() -> new CompressedRedisSerializer<String>(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("被包装的序列化器不能为空");

        CompressedRedisSerializer<String> serializer = new CompressedRedisSerializer<>(raw, 0);
        assertThat(serializer.getThreshold()).isEqualTo(1);
        assertThat(serializer.getDelegate()).isSameAs(raw);
        assertThat(serializer.deserialize(serializer.serialize("x"))).isEqualTo("x");
    }
}
