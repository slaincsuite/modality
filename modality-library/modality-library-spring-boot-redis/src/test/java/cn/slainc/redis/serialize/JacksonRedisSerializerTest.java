package cn.slainc.redis.serialize;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonRedisSerializerTest {

    private final ObjectMapper mapper = RedisSerializers.jsonMapper();

    @Test
    void 空值序列化为空字节数组且反序列化为空() {
        RedisSerializer<Object> serializer = new JacksonRedisSerializer<>(mapper);
        assertThat(serializer.serialize(null)).isEmpty();
        assertThat(serializer.deserialize(null)).isNull();
        assertThat(serializer.deserialize(new byte[0])).isNull();
    }

    @Test
    void 泛型对象往返保持原类型() {
        RedisSerializer<Object> serializer = new JacksonRedisSerializer<>(mapper);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("名称", "模态平台");
        payload.put("数量", 42);
        List<String> tags = new ArrayList<>(List.of("甲", "乙"));
        payload.put("标签", tags);

        byte[] bytes = serializer.serialize(payload);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) restored;
        assertThat(map.get("名称")).isEqualTo("模态平台");
        assertThat(map.get("数量")).isEqualTo(42);
        assertThat(map.get("标签")).isEqualTo(tags);
    }

    @Test
    void 指定JavaType时按声明类型反序列化() {
        RedisSerializer<SamplePayload> serializer = new JacksonRedisSerializer<>(mapper,
                mapper.constructType(SamplePayload.class));
        SamplePayload payload = new SamplePayload();
        payload.setName("订单");
        payload.setAmount(128L);

        SamplePayload restored = serializer.deserialize(serializer.serialize(payload));

        assertThat(restored.getName()).isEqualTo("订单");
        assertThat(restored.getAmount()).isEqualTo(128L);
    }

    @Test
    void 非法内容抛出中文序列化异常() {
        RedisSerializer<SamplePayload> serializer = new JacksonRedisSerializer<>(mapper,
                mapper.constructType(SamplePayload.class));
        assertThatThrownBy(() -> serializer.deserialize("{不是合法 JSON".getBytes()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Redis 值反序列化失败");
    }

    @Test
    void 构造参数为空时抛出中文异常() {
        assertThatThrownBy(() -> new JacksonRedisSerializer<>(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectMapper 不能为空");
    }

    static class SamplePayload {

        private String name;

        private Long amount;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getAmount() {
            return amount;
        }

        public void setAmount(Long amount) {
            this.amount = amount;
        }
    }
}
