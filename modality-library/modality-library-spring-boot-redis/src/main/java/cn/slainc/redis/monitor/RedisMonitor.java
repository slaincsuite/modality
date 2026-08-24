package cn.slainc.redis.monitor;

import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.support.LettuceNativeCommands;
import cn.slainc.redis.support.PortableRedisCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class RedisMonitor {

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisProperties properties;

    public RedisMonitor(StringRedisTemplate stringRedisTemplate, RedisProperties properties) {
        if (stringRedisTemplate == null) {
            throw new IllegalArgumentException("stringRedisTemplate 不能为空");
        }
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public Map<String, String> info() {
        return info(null);
    }

    public Map<String, String> info(String section) {
        Properties properties = stringRedisTemplate.execute((RedisCallback<Properties>) connection ->
                section == null || section.isBlank()
                        ? connection.serverCommands().info()
                        : connection.serverCommands().info(section));
        Map<String, String> result = new LinkedHashMap<>();
        if (properties == null) {
            return result;
        }
        properties.forEach((key, value) -> result.put(String.valueOf(key), value == null ? null : String.valueOf(value)));
        return result;
    }

    public Map<String, String> memoryInfo() {
        return info("memory");
    }

    public Map<String, String> statsInfo() {
        return info("stats");
    }

    public Map<String, String> persistenceInfo() {
        return info("persistence");
    }

    public Map<String, String> clientsInfo() {
        return info("clients");
    }

    public Map<String, String> replicationInfo() {
        return info("replication");
    }

    public long dbSize() {
        Long size = stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                connection.serverCommands().dbSize());
        return size == null ? 0L : size;
    }

    public List<SlowLogEntry> slowlog(int count) {
        List<SlowLogEntry> portable = PortableRedisCommands.slowlog(stringRedisTemplate, count);
        if (!portable.isEmpty()) {
            return portable;
        }
        return slowlogFromNative(count);
    }

    private List<SlowLogEntry> slowlogFromNative(int count) {
        List<Object> raw = stringRedisTemplate.execute((RedisCallback<List<Object>>) connection ->
                LettuceNativeCommands.slowlogGet(connection, Math.max(count, 1)));
        List<SlowLogEntry> entries = new ArrayList<>();
        if (raw == null) {
            return entries;
        }
        for (Object item : raw) {
            if (!(item instanceof List<?> fields) || fields.size() < 4) {
                continue;
            }
            long id = toLong(fields.get(0));
            long timestamp = toLong(fields.get(1));
            long duration = toLong(fields.get(2));
            List<String> command = new ArrayList<>();
            if (fields.get(3) instanceof List<?> args) {
                for (Object arg : args) {
                    command.add(toStringValue(arg));
                }
            }
            entries.add(new SlowLogEntry(id, timestamp, duration, command));
        }
        return entries;
    }

    public RedisMetricsSnapshot snapshot() {
        Map<String, String> stats = statsInfo();
        Map<String, String> clients = clientsInfo();
        Map<String, String> memory = memoryInfo();
        return new RedisMetricsSnapshot(
                toLong(stats.get("keyspace_hits")),
                toLong(stats.get("keyspace_misses")),
                toLong(stats.get("expired_keys")),
                toLong(stats.get("evicted_keys")),
                toLong(clients.get("connected_clients")),
                toLong(clients.get("blocked_clients")),
                toLong(memory.get("used_memory")),
                toLong(stats.get("total_commands_processed")),
                toLong(stats.get("instantaneous_ops_per_sec")),
                dbSize(),
                System.currentTimeMillis());
    }

    public long slowlogThresholdMicros() {
        return properties == null || properties.getMonitor() == null
                ? 100000L
                : properties.getMonitor().getSlowlogThresholdMicros();
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(toStringValue(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static String toStringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }
}
