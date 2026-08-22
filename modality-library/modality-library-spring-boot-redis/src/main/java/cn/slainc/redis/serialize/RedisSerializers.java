package cn.slainc.redis.serialize;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

public final class RedisSerializers {

    private static final RedisSerializer<String> STRING = new StringRedisSerializer();

    private static final ObjectMapper PLAIN_MAPPER = JsonMapper.builder().build();

    private RedisSerializers() {
    }

    public static RedisSerializer<String> string() {
        return STRING;
    }

    public static ObjectMapper jsonMapper() {
        return jsonMapperBuilder().build();
    }

    public static ObjectMapper plainMapper() {
        return PLAIN_MAPPER;
    }

    public static JsonMapper.Builder jsonMapperBuilder() {
        return JsonMapper.builder()
                .activateDefaultTyping(BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                        DefaultTyping.NON_FINAL);
    }

    public static RedisSerializer<Object> json() {
        return new JacksonRedisSerializer<>(jsonMapper());
    }

    public static RedisSerializer<Object> json(ObjectMapper objectMapper) {
        return new JacksonRedisSerializer<>(objectMapper);
    }

    public static RedisSerializer<Object> compressedJson(int threshold) {
        return new CompressedRedisSerializer<>(json(), threshold);
    }
}