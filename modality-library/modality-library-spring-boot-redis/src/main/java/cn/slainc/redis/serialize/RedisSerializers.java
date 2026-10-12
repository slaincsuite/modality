package cn.slainc.redis.serialize;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public final class RedisSerializers {

    private static final RedisSerializer<String> STRING = new StringRedisSerializer();

    private static final ObjectMapper PLAIN_MAPPER = JsonMapper.builder().build();

    private static final List<String> DEFAULT_ALLOWED_PACKAGE_PREFIXES = List.of(
            "cn.slainc.", "cn.dev33.satoken.", "java.util.", "java.lang.", "java.time.", "java.math.",
            "java.sql.");

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
        return jsonMapperBuilder(List.of(), List.of());
    }

    public static JsonMapper.Builder jsonMapperBuilder(Collection<String> allowedPackages,
                                                       Collection<String> allowedTypes) {
        BasicPolymorphicTypeValidator.Builder validator = BasicPolymorphicTypeValidator.builder();
        for (String prefix : DEFAULT_ALLOWED_PACKAGE_PREFIXES) {
            validator.allowIfSubType(prefix);
        }
        for (String prefix : normalize(allowedPackages)) {
            validator.allowIfSubType(prefix);
        }
        List<String> exactTypes = normalize(allowedTypes);
        if (!exactTypes.isEmpty()) {
            StringBuilder regex = new StringBuilder(exactTypes.size() * 24 + 8).append("^(");
            for (int index = 0; index < exactTypes.size(); index++) {
                if (index > 0) {
                    regex.append('|');
                }
                regex.append(Pattern.quote(exactTypes.get(index)));
            }
            validator.allowIfSubType(Pattern.compile(regex.append(")$").toString()));
        }
        // Jackson 3.1 起数组类型会拆到元素类型再校验，放行数组不再构成白名单绕过
        validator.allowIfSubTypeIsArray();
        return JsonMapper.builder().activateDefaultTyping(validator.build(), DefaultTyping.NON_FINAL);
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

    public static RedisSerializer<Object> compressedJson(int threshold, Collection<String> allowedPackages,
                                                         Collection<String> allowedTypes) {
        ObjectMapper mapper = jsonMapperBuilder(allowedPackages, allowedTypes).build();
        return new CompressedRedisSerializer<>(new JacksonRedisSerializer<>(mapper), threshold);
    }

    private static List<String> normalize(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
