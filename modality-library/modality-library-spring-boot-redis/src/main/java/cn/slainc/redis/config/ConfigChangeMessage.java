package cn.slainc.redis.config;

import java.util.UUID;

public class ConfigChangeMessage {

    public enum Operation {
        SET, REMOVE, REFRESH, REFRESH_ALL
    }

    private String key;

    private Operation operation;

    private long version;

    private String nodeId;

    private long timestamp;

    public ConfigChangeMessage() {
    }

    public ConfigChangeMessage(String key, Operation operation, long version, String nodeId, long timestamp) {
        this.key = key;
        this.operation = operation;
        this.version = version;
        this.nodeId = nodeId;
        this.timestamp = timestamp;
    }

    public ConfigChangeMessage(String key, Operation operation, long version, String nodeId) {
        this(key, operation, version, nodeId, System.currentTimeMillis());
    }

    public static ConfigChangeMessage of(String key, Operation operation, long version, String nodeId) {
        return new ConfigChangeMessage(key, operation, version, nodeId, System.currentTimeMillis());
    }

    public static String newNodeId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "配置变更消息[键=" + key + ", 操作=" + operation + ", 版本=" + version + ", 节点=" + nodeId + "]";
    }
}
