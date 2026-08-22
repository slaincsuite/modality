package cn.slainc.redis.serialize;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

public class JacksonRedisSerializer<T> implements RedisSerializer<T> {

    private static final byte[] EMPTY = new byte[0];

    private final ObjectMapper objectMapper;

    private final JavaType javaType;

    public JacksonRedisSerializer(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    public JacksonRedisSerializer(ObjectMapper objectMapper, JavaType javaType) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper 不能为空");
        }
        this.objectMapper = objectMapper;
        this.javaType = javaType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public byte[] serialize(T value) throws SerializationException {
        if (value == null) {
            return EMPTY;
        }
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception ex) {
            throw new SerializationException("Redis 值序列化失败：" + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            if (javaType == null) {
                return (T) objectMapper.readValue(bytes, Object.class);
            }
            return objectMapper.readValue(bytes, javaType);
        } catch (Exception ex) {
            throw new SerializationException("Redis 值反序列化失败：" + ex.getMessage(), ex);
        }
    }
}
