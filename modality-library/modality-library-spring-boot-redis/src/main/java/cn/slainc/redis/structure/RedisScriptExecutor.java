package cn.slainc.redis.structure;

import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RedisScriptExecutor {

    private final StringRedisTemplate stringRedisTemplate;

    private final Map<String, String> shaCache = new ConcurrentHashMap<>();

    public RedisScriptExecutor(StringRedisTemplate stringRedisTemplate) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String load(String script) {
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("Lua 脚本内容不能为空");
        }
        return shaCache.computeIfAbsent(script, key -> stringRedisTemplate.execute(
                (RedisCallback<String>) connection -> connection.scriptingCommands().scriptLoad(bytes(key))));
    }

    public boolean exists(String sha) {
        List<Boolean> result = stringRedisTemplate.execute((RedisCallback<List<Boolean>>) connection ->
                connection.scriptingCommands().scriptExists(sha));
        return result != null && !result.isEmpty() && Boolean.TRUE.equals(result.get(0));
    }

    @SuppressWarnings("unchecked")
    public <T> T execute(String script, RedisScriptResultType resultType, List<String> keys, Object... args) {
        String sha = load(script);
        byte[][] payload = buildArgs(keys, args);
        return stringRedisTemplate.execute((RedisCallback<T>) connection ->
                (T) normalize(connection.scriptingCommands().evalSha(sha, ReturnType.valueOf(resultType.name()),
                        keys == null ? 0 : keys.size(), payload), resultType));
    }

    @SuppressWarnings("unchecked")
    public <T> T executeDirect(String script, RedisScriptResultType resultType, List<String> keys, Object... args) {
        byte[][] payload = buildArgs(keys, args);
        return stringRedisTemplate.execute((RedisCallback<T>) connection ->
                (T) normalize(connection.scriptingCommands().eval(bytes(script), ReturnType.valueOf(resultType.name()),
                        keys == null ? 0 : keys.size(), payload), resultType));
    }

    public String cachedSha(String script) {
        return shaCache.get(script);
    }

    public void clearCache() {
        shaCache.clear();
    }

    public void flush() {
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.scriptingCommands().scriptFlush();
            return null;
        });
        shaCache.clear();
    }

    private static Object normalize(Object result, RedisScriptResultType resultType) {
        if (result instanceof byte[] bytes
                && (resultType == RedisScriptResultType.VALUE || resultType == RedisScriptResultType.STATUS)) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return result;
    }

    private byte[][] buildArgs(List<String> keys, Object... args) {
        List<byte[]> all = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                all.add(bytes(key));
            }
        }
        if (args != null) {
            for (Object arg : args) {
                all.add(bytes(arg == null ? "" : String.valueOf(arg)));
            }
        }
        return all.toArray(new byte[0][]);
    }

    private static byte[] bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
