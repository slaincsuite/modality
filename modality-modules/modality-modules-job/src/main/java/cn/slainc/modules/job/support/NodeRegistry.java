package cn.slainc.modules.job.support;

import cn.slainc.modules.job.autoconfigure.JobModuleProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 节点注册表：Redis 心跳键带 TTL，掉线节点自动消失；无 Redis 时注册表退化为空
public class NodeRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRegistry.class);

    private static final String NODE_SEGMENT = ":node:";

    private static final long SCAN_COUNT = 500L;

    private final StringRedisTemplate redisTemplate;

    private final String keyPrefix;

    private final Duration ttl;

    private final String nodeId;

    public NodeRegistry(StringRedisTemplate redisTemplate, String keyPrefix, Duration ttl, String nodeId) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "modality:job:console" : keyPrefix;
        this.ttl = ttl == null ? Duration.ofMinutes(1) : ttl;
        this.nodeId = nodeId != null && !nodeId.isBlank() ? nodeId : defaultNodeId();
    }

    public String nodeId() {
        return nodeId;
    }

    public void heartbeat() {
        if (redisTemplate == null) {
            LOGGER.debug("没有 StringRedisTemplate，节点心跳退化为本地节点号 nodeId={}", nodeId);
            return;
        }
        redisTemplate.opsForValue().set(key(), LocalDateTime.now().toString(), ttl);
    }

    public void offline() {
        if (redisTemplate != null) {
            redisTemplate.delete(key());
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void shutdown() {
        try {
            offline();
        } catch (RuntimeException e) {
            LOGGER.debug("节点注销失败 nodeId={}（{}），残留键会随 TTL 过期", nodeId, e.toString());
        }
    }

    public List<String> nodes() {
        Set<String> nodes = new LinkedHashSet<>();
        if (redisTemplate == null) {
            return new ArrayList<>(nodes);
        }
        String pattern = keyPrefix + NODE_SEGMENT + "*";
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(SCAN_COUNT).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String node = key.substring((keyPrefix + NODE_SEGMENT).length());
                if (!node.isBlank()) {
                    nodes.add(node);
                }
            }
        }
        return new ArrayList<>(nodes);
    }

    public Map<String, String> nodeStatus() {
        Map<String, String> status = new LinkedHashMap<>();
        for (String node : nodes()) {
            String value = redisTemplate == null ? null
                    : redisTemplate.opsForValue().get(keyPrefix + NODE_SEGMENT + node);
            status.put(node, value);
        }
        return status;
    }

    private String key() {
        return keyPrefix + NODE_SEGMENT + nodeId;
    }

    private static String defaultNodeId() {
        String runtime = ManagementFactory.getRuntimeMXBean().getName();
        return runtime == null ? "node" : runtime.replace("@", "-");
    }
}
