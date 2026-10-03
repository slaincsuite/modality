package cn.slainc.modules.infra.service;

import cn.slainc.core.exception.BizException;
import cn.slainc.core.lang.BizAssert;
import cn.slainc.core.result.ResultCode;
import cn.slainc.redis.key.RedisKeyManager;
import cn.slainc.redis.monitor.RedisMonitor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisConsoleService {

    public record KeyBrief(String key, String type, Long ttlSeconds, Long bytes) {
    }

    public static final int MAX_SCAN_LIMIT = 200;

    private final StringRedisTemplate redis;

    private final RedisKeyManager keyManager;

    private final ObjectProvider<RedisMonitor> monitor;

    public RedisConsoleService(StringRedisTemplate redis, RedisKeyManager keyManager,
                               ObjectProvider<RedisMonitor> monitor) {
        this.redis = redis;
        this.keyManager = keyManager;
        this.monitor = monitor;
    }

    public Map<String, Object> stats() {
        RedisMonitor service = requireMonitor();
        Map<String, String> memory = service.memoryInfo();
        Map<String, String> server = service.info("server");
        Map<String, String> clients = service.clientsInfo();
        Map<String, String> statsInfo = service.statsInfo();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("dbSize", service.dbSize());
        view.put("redisVersion", server.get("redis_version"));
        view.put("mode", clusterEnabled() ? "CLUSTER" : "STANDALONE");
        view.put("usedMemoryHuman", memory.get("used_memory_human"));
        view.put("usedMemoryBytes", parseLong(memory.get("used_memory")));
        view.put("maxMemoryHuman", memory.get("maxmemory_human"));
        view.put("maxMemoryBytes", parseLong(memory.get("maxmemory")));
        view.put("memFragmentationRatio", memory.get("mem_fragmentation_ratio"));
        view.put("connectedClients", clients.get("connected_clients"));
        view.put("blockedClients", clients.get("blocked_clients"));
        view.put("instantaneousOpsPerSecond", statsInfo.get("instantaneous_ops_per_sec"));
        view.put("totalConnectionsReceived", statsInfo.get("total_connections_received"));
        view.put("expiredKeys", statsInfo.get("expired_keys"));
        view.put("evictedKeys", statsInfo.get("evicted_keys"));
        view.put("keyspaceHits", statsInfo.get("keyspace_hits"));
        view.put("keyspaceMisses", statsInfo.get("keyspace_misses"));
        view.put("slowlogThresholdMicros", service.slowlogThresholdMicros());
        return view;
    }

    public Map<String, String> info(String section) {
        return requireMonitor().info(section);
    }

    public List<KeyBrief> keys(String prefix, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_SCAN_LIMIT);
        String pattern = (prefix == null ? "" : prefix.trim()) + "*";
        List<KeyBrief> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(pattern).count(500).build())) {
            while (cursor.hasNext() && keys.size() < safeLimit) {
                String key = cursor.next();
                keys.add(new KeyBrief(key, keyManager.type(key), keyManager.ttlSeconds(key),
                        keyManager.memoryUsage(key)));
            }
        }
        return keys;
    }

    public Map<String, Object> keyDetail(String key) {
        BizAssert.hasText(key, "键名不能为空");
        BizAssert.isTrue(redis.hasKey(key), "键不存在：" + key);
        String type = keyManager.type(key);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("key", key);
        view.put("type", type);
        Long ttlSeconds = keyManager.ttlSeconds(key);
        view.put("ttlSeconds", ttlSeconds);
        view.put("bytes", keyManager.memoryUsage(key));
        view.put("valuePreview", valuePreview(key, type));
        return view;
    }

    public boolean deleteKey(String key) {
        BizAssert.hasText(key, "键名不能为空");
        Boolean removed = redis.delete(key);
        return Boolean.TRUE.equals(removed);
    }

    public List<Map<String, Object>> slowlog(int count) {
        int safeCount = Math.min(Math.max(count, 1), 100);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (cn.slainc.redis.monitor.SlowLogEntry entry : requireMonitor().slowlog(safeCount)) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("id", entry.getId());
            view.put("timestamp", entry.getTimestamp().toString());
            view.put("durationMicros", entry.getDurationMicros());
            view.put("command", entry.getCommand());
            entries.add(view);
        }
        return entries;
    }

    public List<Map<String, String>> clients() {
        String raw = executeCommand("CLIENT", "LIST");
        List<Map<String, String>> clients = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return clients;
        }
        for (String line : raw.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            Map<String, String> client = new LinkedHashMap<>();
            for (String field : line.trim().split("\\s+")) {
                int eq = field.indexOf('=');
                if (eq > 0) {
                    client.put(field.substring(0, eq), field.substring(eq + 1));
                }
            }
            clients.add(client);
        }
        return clients;
    }

    public Map<String, Object> cluster() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mode", clusterEnabled() ? "CLUSTER" : "STANDALONE");
        view.put("clusterEnabled", clusterEnabled());
        if (clusterEnabled()) {
            String info = executeCommand("CLUSTER", "INFO");
            if (info != null) {
                for (String line : info.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        view.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                    }
                }
            }
        }
        return view;
    }

    private boolean clusterEnabled() {
        try {
            Map<String, String> clusterInfo = requireMonitor().info("cluster");
            return "1".equals(clusterInfo.get("cluster_enabled"));
        } catch (DataAccessException exception) {
            return false;
        }
    }

    private RedisMonitor requireMonitor() {
        RedisMonitor service = monitor.getIfAvailable();
        if (service == null) {
            throw new BizException(ResultCode.SERVICE_UNAVAILABLE, "Redis 监控服务未启用（modality.redis.monitor.enabled=false）");
        }
        return service;
    }

    private String valuePreview(String key, String type) {
        try {
            DataType dataType = DataType.fromCode(type);
            return switch (dataType) {
                case STRING -> previewText(redis.opsForValue().get(key));
                case HASH -> previewEntries(redis.opsForHash().entries(key));
                case LIST -> previewList(redis.opsForList().range(key, 0, 9));
                case SET -> previewList(new ArrayList<>(redis.opsForSet().members(key) == null ? List.of()
                        : redis.opsForSet().members(key)));
                case ZSET -> previewList(redis.opsForZSet().range(key, 0, 9) == null ? List.of()
                        : new ArrayList<>(redis.opsForZSet().range(key, 0, 9)));
                default -> "该类型暂不支持值预览";
            };
        } catch (Exception exception) {
            return "值预览失败：" + exception.getMessage();
        }
    }

    private static String previewText(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "…";
    }

    private static String previewList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<String> limited = values.stream().limit(10).toList();
        return previewText(limited.toString());
    }

    private static String previewEntries(java.util.Map<Object, Object> entries) {
        if (entries == null || entries.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        int count = 0;
        for (java.util.Map.Entry<Object, Object> entry : entries.entrySet()) {
            if (count++ >= 10) {
                builder.append("…");
                break;
            }
            if (count > 1) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return previewText(builder.append('}').toString());
    }

    private String executeCommand(String... command) {
        try {
            return redis.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> {
                byte[][] args = new byte[command.length - 1][];
                for (int i = 1; i < command.length; i++) {
                    args[i - 1] = command[i].getBytes(StandardCharsets.UTF_8);
                }
                Object result = connection.execute(command[0], args);
                if (result instanceof byte[] bytes) {
                    return new String(bytes, StandardCharsets.UTF_8);
                }
                return result == null ? null : String.valueOf(result);
            });
        } catch (DataAccessException exception) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
