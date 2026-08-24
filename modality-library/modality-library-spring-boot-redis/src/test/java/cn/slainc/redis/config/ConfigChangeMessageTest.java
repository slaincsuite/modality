package cn.slainc.redis.config;

import cn.slainc.redis.autoconfigure.RedisProperties;
import cn.slainc.redis.serialize.RedisSerializers;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigChangeMessageTest {

    private final ObjectMapper mapper = RedisSerializers.plainMapper();

    @Test
    void 消息对象可JSON往返并保留全部字段() {
        ConfigChangeMessage message = ConfigChangeMessage.of("order.timeout", ConfigChangeMessage.Operation.SET, 12L,
                "节点甲");
        String json = mapper.writeValueAsString(message);
        ConfigChangeMessage restored = mapper.readValue(json, ConfigChangeMessage.class);

        assertThat(restored.getKey()).isEqualTo("order.timeout");
        assertThat(restored.getOperation()).isEqualTo(ConfigChangeMessage.Operation.SET);
        assertThat(restored.getVersion()).isEqualTo(12L);
        assertThat(restored.getNodeId()).isEqualTo("节点甲");
        assertThat(restored.getTimestamp()).isPositive();
        assertThat(restored.toString()).contains("配置变更消息");
    }

    @Test
    void 操作类型与节点标识生成() {
        assertThat(ConfigChangeMessage.Operation.values()).containsExactly(
                ConfigChangeMessage.Operation.SET,
                ConfigChangeMessage.Operation.REMOVE,
                ConfigChangeMessage.Operation.REFRESH,
                ConfigChangeMessage.Operation.REFRESH_ALL);
        assertThat(ConfigChangeMessage.newNodeId()).hasSize(32).isNotEqualTo(ConfigChangeMessage.newNodeId());

        ConfigChangeMessage empty = new ConfigChangeMessage();
        empty.setKey("键");
        empty.setOperation(ConfigChangeMessage.Operation.REMOVE);
        empty.setVersion(3L);
        empty.setNodeId("节点乙");
        empty.setTimestamp(1L);
        assertThat(empty.getKey()).isEqualTo("键");
        assertThat(empty.getOperation()).isEqualTo(ConfigChangeMessage.Operation.REMOVE);
        assertThat(empty.getVersion()).isEqualTo(3L);
        assertThat(empty.getNodeId()).isEqualTo("节点乙");
        assertThat(empty.getTimestamp()).isEqualTo(1L);
    }

    @Test
    void 频道命名默认值符合验收要求() {
        RedisProperties properties = new RedisProperties();
        assertThat(RedisDynamicConfigService.channelOf(properties)).isEqualTo("modality:config:broadcast");
        assertThat(DynamicConfigSchema.DEFAULT_TABLE).isEqualTo("t_dynamic_config");
    }
}
