package cn.slainc.redis.serialize;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class RedisSerializersAllowlistTest {

    private final ObjectMapper mapper = RedisSerializers.jsonMapper();

    @Test
    void allowlistedProjectAndContainerTypesRoundTrip() {
        RedisSerializer<Object> serializer = new JacksonRedisSerializer<>(mapper);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("名称", "模态平台");
        payload.put("数量", 42);
        payload.put("标签", new ArrayList<>(List.of("甲", "乙")));

        Object restored = serializer.deserialize(serializer.serialize(payload));

        assertThat(restored).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) restored).get("标签")).isEqualTo(List.of("甲", "乙"));
    }

    @Test
    void saTokenSessionTypeStaysAllowed() throws Exception {
        Class<?> sessionType = Class.forName("cn.dev33.satoken.session.SaSession");
        Object session = sessionType.getConstructor(String.class).newInstance("session-1");
        RedisSerializer<Object> serializer = new JacksonRedisSerializer<>(mapper);

        Object restored = serializer.deserialize(serializer.serialize(session));

        assertThat(restored).isInstanceOf(sessionType);
    }

    @Test
    void nonAllowlistedTypeIsRejected() {
        RedisSerializer<Object> serializer = new JacksonRedisSerializer<>(mapper);
        byte[] payload = "[\"org.springframework.context.support.ClassPathXmlApplicationContext\",{}]"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Throwable failure = catchThrowable(() -> serializer.deserialize(payload));

        assertThat(failure).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Redis 值反序列化失败");
        assertThat(rootMessage(failure)).contains("denied");
    }

    @Test
    void extraAllowedPackagesWidenTheValidator() {
        ObjectMapper extended = RedisSerializers.jsonMapperBuilder(List.of("java.io."), List.of()).build();
        RedisSerializer<Object> serializer = new JacksonRedisSerializer<>(extended);
        byte[] payload = "[\"java.io.File\",{\"path\":\"/tmp/modality.txt\"}]"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Throwable failure = catchThrowable(() -> serializer.deserialize(payload));

        assertThat(rootMessage(failure)).doesNotContain("denied");
    }

    @Test
    void exactAllowedTypesAreHonoured() {
        ObjectMapper extended = RedisSerializers.jsonMapperBuilder(List.of(),
                List.of("java.io.File")).build();
        Throwable failure = catchThrowable(() -> new JacksonRedisSerializer<>(extended)
                .deserialize("[\"java.io.File\",{\"path\":\"/tmp/modality.txt\"}]"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThat(rootMessage(failure)).doesNotContain("denied");
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        StringBuilder message = new StringBuilder();
        while (current != null) {
            if (current.getMessage() != null) {
                message.append(current.getMessage());
            }
            current = current.getCause();
        }
        return message.toString();
    }
}
