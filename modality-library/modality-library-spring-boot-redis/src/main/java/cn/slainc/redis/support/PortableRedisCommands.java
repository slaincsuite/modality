package cn.slainc.redis.support;

import cn.slainc.redis.monitor.SlowLogEntry;
import cn.slainc.utils.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PortableRedisCommands {

    private static final Logger log = LoggerFactory.getLogger(PortableRedisCommands.class);

    // 统一返回字符串，避免不同客户端对整数应答解码不一致（SDR 4 的 ByteArrayOutput 不支持 set(long)）
    private static final String MEMORY_USAGE_SCRIPT = "local v = redis.call('MEMORY','USAGE',KEYS[1]) "
            + "if not v then return '' end return tostring(v)";

    private static final String SLOWLOG_SCRIPT = "local e = redis.call('SLOWLOG','GET',tonumber(ARGV[1])) "
            + "local o = {} for i,v in ipairs(e) do "
            + "local c = {} for j,a in ipairs(v[4]) do c[j] = tostring(a) end "
            + "o[i] = cjson.encode({id=v[1], ts=v[2], us=v[3], cmd=c}) end "
            + "return '[' .. table.concat(o, ',') .. ']'";

    private PortableRedisCommands() {
    }

    public static Long memoryUsage(StringRedisTemplate template, String key) {
        if (template == null || key == null) {
            return null;
        }
        byte[] reply = eval(template, MEMORY_USAGE_SCRIPT, 1, key);
        if (reply == null) {
            return null;
        }
        String text = new String(reply, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            log.warn("MEMORY USAGE 返回值无法解析：{}", text);
            return null;
        }
    }

    public static List<SlowLogEntry> slowlog(StringRedisTemplate template, int count) {
        List<SlowLogEntry> entries = new ArrayList<>();
        if (template == null) {
            return entries;
        }
        byte[] reply = eval(template, SLOWLOG_SCRIPT, 0, String.valueOf(Math.max(count, 1)));
        if (reply == null) {
            return entries;
        }
        String json = new String(reply, StandardCharsets.UTF_8).trim();
        if (json.isEmpty() || "{}".equals(json) || "[]".equals(json)) {
            return entries;
        }
        try {
            for (Map<String, Object> item : JsonUtils.parseList(json, Map.class)) {
                entries.add(new SlowLogEntry(toLong(item.get("id")), toLong(item.get("ts")), toLong(item.get("us")),
                        toStringList(item.get("cmd"))));
            }
        } catch (RuntimeException exception) {
            log.warn("SLOWLOG 返回值无法解析：{}", exception.getMessage());
            return new ArrayList<>();
        }
        return entries;
    }

    private static byte[] eval(StringRedisTemplate template, String script, int keyCount, String keyOrArg) {
        try {
            return template.execute((RedisCallback<byte[]>) connection -> connection.scriptingCommands()
                    .eval(script.getBytes(StandardCharsets.UTF_8), ReturnType.VALUE, keyCount,
                            keyOrArg.getBytes(StandardCharsets.UTF_8)));
        } catch (RuntimeException exception) {
            log.warn("通过 Lua 执行原生命令失败，将回退到客户端原生接口：{}", exception.getMessage());
            return null;
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                result.add(item == null ? "" : String.valueOf(item));
            }
        }
        return result;
    }
}
